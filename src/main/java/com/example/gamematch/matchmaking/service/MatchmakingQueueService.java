package com.example.gamematch.matchmaking.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.gamematch.matchmaking.model.QueueEntry;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
public class MatchmakingQueueService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingQueueService.class);

    private static final String KEY_PLAYER_INFO = "gamematch:queue:player_info";
    private static final String KEY_ACTIVE_PLAYERS = "gamematch:queue:active_players";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> joinQueueScript;
    private final RedisScript<Long> leaveQueueScript;
    private final RedisScript<Long> popMatchCandidatesScript;
    private final RedisScript<Long> requeuePlayersScript;
    private final ObjectMapper objectMapper;
    private final MatchmakingMetrics metrics;

    public MatchmakingQueueService(StringRedisTemplate redis,
                                   RedisScript<Long> joinQueueScript,
                                   RedisScript<Long> leaveQueueScript,
                                   RedisScript<Long> popMatchCandidatesScript,
                                   RedisScript<Long> requeuePlayersScript,
                                   ObjectMapper objectMapper,
                                   MatchmakingMetrics metrics) {
        this.redis = redis;
        this.joinQueueScript = joinQueueScript;
        this.leaveQueueScript = leaveQueueScript;
        this.popMatchCandidatesScript = popMatchCandidatesScript;
        this.requeuePlayersScript = requeuePlayersScript;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public static String queueKey(Region region, GameMode mode) {
        return "gamematch:queue:" + region.name() + ":" + mode.name();
    }

    public boolean joinQueue(QueueEntry entry) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            String qKey = queueKey(entry.region(), entry.mode());

            Long result = redis.execute(
                    joinQueueScript,
                    List.of(qKey, KEY_PLAYER_INFO, KEY_ACTIVE_PLAYERS),
                    entry.playerId().toString(),
                    String.valueOf(entry.skillRating()),
                    json
            );

            boolean joined = result != null && result == 1L;
            if (joined) {
                metrics.setQueueSize(getTotalQueueSize());
                log.info("Player {} successfully joined queue {} with rating {}",
                        entry.playerId(), qKey, entry.skillRating());
            } else {
                log.debug("Player {} was already present in queue", entry.playerId());
            }
            return joined;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize queue entry", e);
        }
    }

    public boolean leaveQueue(Long playerId) {
        Optional<QueueEntry> existing = getQueueEntry(playerId);
        String qKey = existing.map(e -> queueKey(e.region(), e.mode())).orElse("");

        Long result = redis.execute(
                leaveQueueScript,
                List.of(qKey, KEY_PLAYER_INFO, KEY_ACTIVE_PLAYERS),
                playerId.toString()
        );

        boolean removed = result != null && result == 1L;
        if (removed) {
            metrics.setQueueSize(getTotalQueueSize());
            log.info("Player {} left the matchmaking queue", playerId);
        }
        return removed;
    }

    public boolean isPlayerQueued(Long playerId) {
        Boolean member = redis.opsForSet().isMember(KEY_ACTIVE_PLAYERS, playerId.toString());
        return Boolean.TRUE.equals(member);
    }

    public Optional<QueueEntry> getQueueEntry(Long playerId) {
        Object raw = redis.opsForHash().get(KEY_PLAYER_INFO, playerId.toString());
        if (raw == null) {
            return Optional.empty();
        }
        try {
            QueueEntry entry = objectMapper.readValue(raw.toString(), QueueEntry.class);
            return Optional.of(entry);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse queue entry for player {}: {}", playerId, e.getMessage());
            return Optional.empty();
        }
    }

    public Set<String> findCandidatesInRange(Region region, GameMode mode, int minRating, int maxRating, int limit) {
        String qKey = queueKey(region, mode);
        Set<String> members = redis.opsForZSet().rangeByScore(qKey, minRating, maxRating, 0, limit);
        return members != null ? members : Collections.emptySet();
    }

    public Set<ZSetOperations.TypedTuple<String>> getQueueMembersWithScores(Region region, GameMode mode, long start, long end) {
        String qKey = queueKey(region, mode);
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet().rangeWithScores(qKey, start, end);
        return tuples != null ? tuples : Collections.emptySet();
    }

    public boolean popMatchedCandidates(Region region, GameMode mode, List<Long> candidatePlayerIds) {
        String qKey = queueKey(region, mode);
        String[] args = candidatePlayerIds.stream().map(Object::toString).toArray(String[]::new);

        Long result = redis.execute(
                popMatchCandidatesScript,
                List.of(qKey, KEY_PLAYER_INFO, KEY_ACTIVE_PLAYERS),
                (Object[]) args
        );

        boolean popped = result != null && result == 1L;
        if (popped) {
            metrics.setQueueSize(getTotalQueueSize());
            log.info("Atomically popped matched candidates {} from {}", candidatePlayerIds, qKey);
        }
        return popped;
    }

    public void requeue(Region region, GameMode mode, List<QueueEntry> entries) {
        if (entries.isEmpty()) return;
        String qKey = queueKey(region, mode);

        List<String> args = new ArrayList<>();
        for (QueueEntry entry : entries) {
            try {
                args.add(entry.playerId().toString());
                args.add(String.valueOf(entry.skillRating()));
                args.add(objectMapper.writeValueAsString(entry));
            } catch (JsonProcessingException e) {
                log.error("Error serializing queue entry for requeue: {}", e.getMessage());
            }
        }

        redis.execute(
                requeuePlayersScript,
                List.of(qKey, KEY_PLAYER_INFO, KEY_ACTIVE_PLAYERS),
                (Object[]) args.toArray(new String[0])
        );
        metrics.setQueueSize(getTotalQueueSize());
        log.warn("Requeued players {} to {}", entries.stream().map(QueueEntry::playerId).toList(), qKey);
    }

    public long getQueueSize(Region region, GameMode mode) {
        Long size = redis.opsForZSet().size(queueKey(region, mode));
        return size != null ? size : 0L;
    }

    public long getTotalQueueSize() {
        Long size = redis.opsForSet().size(KEY_ACTIVE_PLAYERS);
        return size != null ? size : 0L;
    }
}
