package com.example.gamematch.matchmaking.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.gamematch.config.MatchmakingProperties;
import com.example.gamematch.match.model.Match;
import com.example.gamematch.match.service.MatchService;
import com.example.gamematch.matchmaking.model.QueueEntry;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.repository.PlayerRepository;
import com.example.gamematch.session.service.GameSessionService;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MatchmakingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingEngine.class);

    private final MatchmakingQueueService queueService;
    private final MatchService matchService;
    private final GameSessionService sessionService;
    private final PlayerRepository playerRepository;
    private final MatchmakingProperties properties;
    private final MatchmakingMetrics metrics;
    private final Clock clock;

    public MatchmakingEngine(MatchmakingQueueService queueService,
                             MatchService matchService,
                             GameSessionService sessionService,
                             PlayerRepository playerRepository,
                             MatchmakingProperties properties,
                             MatchmakingMetrics metrics,
                             Clock clock) {
        this.queueService = queueService;
        this.matchService = matchService;
        this.sessionService = sessionService;
        this.playerRepository = playerRepository;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gamematch.matchmaking.evaluation-interval-ms:2000}")
    public void runMatchmakingCycle() {
        Timer.Sample sample = Timer.start();
        try {
            metrics.incrementAttempts();
            evaluateAllQueues();
        } catch (Exception e) {
            log.error("Error during matchmaking cycle: ", e);
        } finally {
            sample.stop(metrics.getMatchmakingTimer());
        }
    }

    public int evaluateAllQueues() {
        int matchesCreated = 0;
        for (Region region : Region.values()) {
            for (GameMode mode : GameMode.values()) {
                matchesCreated += evaluateQueue(region, mode);
            }
        }
        return matchesCreated;
    }

    public int evaluateQueue(Region region, GameMode mode) {
        int requiredPlayers = mode.getRequiredPlayers();
        long queueSize = queueService.getQueueSize(region, mode);
        if (queueSize < requiredPlayers) {
            return 0;
        }

        Set<ZSetOperations.TypedTuple<String>> entries = queueService.getQueueMembersWithScores(region, mode, 0, -1);
        if (entries.size() < requiredPlayers) {
            return 0;
        }

        int matchesFormed = 0;
        Set<Long> alreadyGroupedInThisPass = new HashSet<>();

        List<ZSetOperations.TypedTuple<String>> sortedList = new ArrayList<>(entries);

        for (int i = 0; i < sortedList.size(); i++) {
            ZSetOperations.TypedTuple<String> pivotTuple = sortedList.get(i);
            if (pivotTuple.getValue() == null) continue;

            Long pivotPlayerId;
            try {
                pivotPlayerId = Long.parseLong(pivotTuple.getValue());
            } catch (NumberFormatException e) {
                continue;
            }

            if (alreadyGroupedInThisPass.contains(pivotPlayerId)) {
                continue;
            }

            Optional<QueueEntry> pivotEntryOpt = queueService.getQueueEntry(pivotPlayerId);
            if (pivotEntryOpt.isEmpty()) {
                continue;
            }
            QueueEntry pivotEntry = pivotEntryOpt.get();

            // Calculate expanded tolerance based on wait time: base tolerance + 25 per 5s of wait
            long waitSeconds = Math.max(0, Duration.between(pivotEntry.joinedAt(), clock.instant()).toSeconds());
            int dynamicTolerance = properties.matchmaking().ratingTolerance() + (int) (waitSeconds / 5) * 25;

            List<Long> matchedCandidateIds = new ArrayList<>();
            matchedCandidateIds.add(pivotPlayerId);

            int pivotRating = pivotEntry.skillRating();

            for (int j = 0; j < sortedList.size() && matchedCandidateIds.size() < requiredPlayers; j++) {
                if (i == j) continue;
                ZSetOperations.TypedTuple<String> otherTuple = sortedList.get(j);
                if (otherTuple.getValue() == null) continue;

                Long otherPlayerId;
                try {
                    otherPlayerId = Long.parseLong(otherTuple.getValue());
                } catch (NumberFormatException e) {
                    continue;
                }

                if (alreadyGroupedInThisPass.contains(otherPlayerId) || matchedCandidateIds.contains(otherPlayerId)) {
                    continue;
                }

                int otherRating = otherTuple.getScore() != null ? otherTuple.getScore().intValue() : 1000;
                if (Math.abs(pivotRating - otherRating) <= dynamicTolerance) {
                    matchedCandidateIds.add(otherPlayerId);
                }
            }

            if (matchedCandidateIds.size() == requiredPlayers) {
                alreadyGroupedInThisPass.addAll(matchedCandidateIds);
                boolean success = processMatch(region, mode, matchedCandidateIds);
                if (success) {
                    matchesFormed++;
                }
            }
        }

        return matchesFormed;
    }

    private boolean processMatch(Region region, GameMode mode, List<Long> candidateIds) {
        // Collect existing queue entries for potential requeue
        List<QueueEntry> backupEntries = new ArrayList<>();
        for (Long id : candidateIds) {
            queueService.getQueueEntry(id).ifPresent(backupEntries::add);
        }

        // Atomically pop candidates from Redis
        boolean popped = queueService.popMatchedCandidates(region, mode, candidateIds);
        if (!popped) {
            log.debug("Failed to pop candidate group {} (one or more players left or already matched)", candidateIds);
            return false;
        }

        try {
            // Load player entities from PostgreSQL
            List<Player> players = playerRepository.findAllById(candidateIds);
            if (players.size() != candidateIds.size()) {
                throw new IllegalStateException("Not all matched players exist in DB: expected " + candidateIds + " but found " + players.size());
            }

            // Create Match and Session in PostgreSQL
            Match match = matchService.createMatch(region, mode, players);
            sessionService.createSession(match, players);

            metrics.incrementSuccessfulMatches();
            log.info("Successfully created match {} and session for players {}", match.getId(), candidateIds);
            return true;
        } catch (Exception e) {
            log.error("Match creation transaction failed for candidates {}. Requeuing players: ", candidateIds, e);
            metrics.incrementFailedMatches();
            // Restore players to Redis queue
            queueService.requeue(region, mode, backupEntries);
            return false;
        }
    }
}
