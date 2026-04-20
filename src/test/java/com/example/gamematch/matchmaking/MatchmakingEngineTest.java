package com.example.gamematch.matchmaking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.gamematch.config.MatchmakingProperties;
import com.example.gamematch.match.model.Match;
import com.example.gamematch.match.service.MatchService;
import com.example.gamematch.matchmaking.model.QueueEntry;
import com.example.gamematch.matchmaking.service.MatchmakingEngine;
import com.example.gamematch.matchmaking.service.MatchmakingQueueService;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.repository.PlayerRepository;
import com.example.gamematch.session.service.GameSessionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchmakingEngineTest {

    @Mock
    private MatchmakingQueueService queueService;
    @Mock
    private MatchService matchService;
    @Mock
    private GameSessionService sessionService;
    @Mock
    private PlayerRepository playerRepository;

    private MatchmakingMetrics metrics;
    private MatchmakingProperties properties;
    private Clock fixedClock;
    private MatchmakingEngine engine;

    @BeforeEach
    void setUp() {
        metrics = new MatchmakingMetrics(new SimpleMeterRegistry());
        properties = new MatchmakingProperties(
                new MatchmakingProperties.Matchmaking(100, 1, 2000L),
                new MatchmakingProperties.Session(15L, 5L)
        );
        fixedClock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneId.of("UTC"));
        engine = new MatchmakingEngine(queueService, matchService, sessionService, playerRepository, properties, metrics, fixedClock);
    }

    @Test
    @DisplayName("Matches compatible players when rating difference is within tolerance")
    void matchesCompatiblePlayersWithinTolerance() {
        Region region = Region.NA;
        GameMode mode = GameMode.RANKED_1V1; // requires 2 players

        when(queueService.getQueueSize(region, mode)).thenReturn(2L);

        Set<ZSetOperations.TypedTuple<String>> entries = Set.of(
                new DefaultTypedTuple<>("1", 1200.0),
                new DefaultTypedTuple<>("2", 1250.0) // delta = 50 <= 100
        );
        when(queueService.getQueueMembersWithScores(region, mode, 0, -1)).thenReturn(entries);

        QueueEntry q1 = new QueueEntry(1L, region, mode, 1200, fixedClock.instant());
        QueueEntry q2 = new QueueEntry(2L, region, mode, 1250, fixedClock.instant());
        when(queueService.getQueueEntry(1L)).thenReturn(Optional.of(q1));
        when(queueService.getQueueEntry(2L)).thenReturn(Optional.of(q2));

        when(queueService.popMatchedCandidates(eq(region), eq(mode), anyList())).thenReturn(true);

        Player p1 = new Player(1L, "PlayerOne", region, 1200, mode, fixedClock.instant());
        Player p2 = new Player(2L, "PlayerTwo", region, 1250, mode, fixedClock.instant());
        when(playerRepository.findAllById(anyList())).thenReturn(List.of(p1, p2));

        Match match = new Match(1L, region, mode, 1225, fixedClock.instant());
        when(matchService.createMatch(eq(region), eq(mode), any())).thenReturn(match);

        int formed = engine.evaluateQueue(region, mode);

        assertThat(formed).isEqualTo(1);
        verify(matchService).createMatch(eq(region), eq(mode), any());
        verify(sessionService).createSession(eq(match), any());
    }

    @Test
    @DisplayName("Does not match players when rating difference exceeds tolerance")
    void doesNotMatchWhenRatingDifferenceExceedsTolerance() {
        Region region = Region.EU;
        GameMode mode = GameMode.RANKED_1V1;

        when(queueService.getQueueSize(region, mode)).thenReturn(2L);

        Set<ZSetOperations.TypedTuple<String>> entries = Set.of(
                new DefaultTypedTuple<>("1", 1000.0),
                new DefaultTypedTuple<>("2", 1500.0) // delta = 500 > 100
        );
        when(queueService.getQueueMembersWithScores(region, mode, 0, -1)).thenReturn(entries);

        QueueEntry q1 = new QueueEntry(1L, region, mode, 1000, fixedClock.instant());
        QueueEntry q2 = new QueueEntry(2L, region, mode, 1500, fixedClock.instant());
        when(queueService.getQueueEntry(1L)).thenReturn(Optional.of(q1));
        when(queueService.getQueueEntry(2L)).thenReturn(Optional.of(q2));

        int formed = engine.evaluateQueue(region, mode);

        assertThat(formed).isEqualTo(0);
    }

    @Test
    @DisplayName("Requeues candidates if match creation persistence fails")
    void requeuesCandidatesIfMatchCreationFails() {
        Region region = Region.NA;
        GameMode mode = GameMode.RANKED_1V1;

        when(queueService.getQueueSize(region, mode)).thenReturn(2L);

        Set<ZSetOperations.TypedTuple<String>> entries = Set.of(
                new DefaultTypedTuple<>("1", 1200.0),
                new DefaultTypedTuple<>("2", 1220.0)
        );
        when(queueService.getQueueMembersWithScores(region, mode, 0, -1)).thenReturn(entries);

        QueueEntry q1 = new QueueEntry(1L, region, mode, 1200, fixedClock.instant());
        QueueEntry q2 = new QueueEntry(2L, region, mode, 1220, fixedClock.instant());
        when(queueService.getQueueEntry(1L)).thenReturn(Optional.of(q1));
        when(queueService.getQueueEntry(2L)).thenReturn(Optional.of(q2));

        when(queueService.popMatchedCandidates(eq(region), eq(mode), anyList())).thenReturn(true);
        when(playerRepository.findAllById(anyList())).thenThrow(new RuntimeException("DB Connection failed"));

        int formed = engine.evaluateQueue(region, mode);

        assertThat(formed).isEqualTo(0);
        verify(queueService).requeue(eq(region), eq(mode), any());
    }
}
