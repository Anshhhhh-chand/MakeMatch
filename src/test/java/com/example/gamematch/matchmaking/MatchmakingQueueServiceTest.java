package com.example.gamematch.matchmaking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.example.gamematch.matchmaking.model.QueueEntry;
import com.example.gamematch.matchmaking.service.MatchmakingQueueService;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchmakingQueueServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private RedisScript<Long> joinQueueScript;
    @Mock
    private RedisScript<Long> leaveQueueScript;
    @Mock
    private RedisScript<Long> popMatchCandidatesScript;
    @Mock
    private RedisScript<Long> requeuePlayersScript;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private ZSetOperations<String, String> zsetOps;

    private ObjectMapper objectMapper;
    private MatchmakingMetrics metrics;
    private MatchmakingQueueService queueService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        metrics = new MatchmakingMetrics(new SimpleMeterRegistry());
        queueService = new MatchmakingQueueService(
                redis, joinQueueScript, leaveQueueScript,
                popMatchCandidatesScript, requeuePlayersScript,
                objectMapper, metrics
        );
    }

    @Test
    @DisplayName("joinQueue returns true when player is successfully added to Redis")
    void joinQueueSuccess() {
        QueueEntry entry = new QueueEntry(101L, Region.NA, GameMode.RANKED_1V1, 1300, Instant.now());
        when(redis.execute(eq(joinQueueScript), anyList(), eq("101"), eq("1300"), any())).thenReturn(1L);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.size("gamematch:queue:active_players")).thenReturn(1L);

        boolean result = queueService.joinQueue(entry);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("joinQueue returns false when player is already queued (duplicate prevention)")
    void joinQueueDuplicatePrevented() {
        QueueEntry entry = new QueueEntry(101L, Region.NA, GameMode.RANKED_1V1, 1300, Instant.now());
        when(redis.execute(eq(joinQueueScript), anyList(), eq("101"), eq("1300"), any())).thenReturn(0L);

        boolean result = queueService.joinQueue(entry);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("leaveQueue removes player and returns true")
    void leaveQueueSuccess() throws Exception {
        QueueEntry entry = new QueueEntry(101L, Region.NA, GameMode.RANKED_1V1, 1300, Instant.now());
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.get("gamematch:queue:player_info", "101")).thenReturn(objectMapper.writeValueAsString(entry));

        when(redis.execute(eq(leaveQueueScript), anyList(), eq("101"))).thenReturn(1L);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.size("gamematch:queue:active_players")).thenReturn(0L);

        boolean result = queueService.leaveQueue(101L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("popMatchedCandidates executes atomic Lua script")
    void popMatchedCandidates() {
        when(redis.execute(eq(popMatchCandidatesScript), anyList(), eq("1"), eq("2"))).thenReturn(1L);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.size("gamematch:queue:active_players")).thenReturn(0L);

        boolean result = queueService.popMatchedCandidates(Region.NA, GameMode.RANKED_1V1, List.of(1L, 2L));

        assertThat(result).isTrue();
    }
}
