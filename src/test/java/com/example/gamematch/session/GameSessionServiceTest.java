package com.example.gamematch.session;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.example.gamematch.match.model.Match;
import com.example.gamematch.match.model.MatchStatus;
import com.example.gamematch.match.service.MatchService;
import com.example.gamematch.notification.service.NotificationService;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.repository.PlayerRepository;
import com.example.gamematch.player.service.PlayerService;
import com.example.gamematch.session.dto.SessionResponse;
import com.example.gamematch.session.model.GameSession;
import com.example.gamematch.session.model.MatchHistory;
import com.example.gamematch.session.model.SessionPlayer;
import com.example.gamematch.session.model.SessionStatus;
import com.example.gamematch.session.repository.GameSessionRepository;
import com.example.gamematch.session.repository.MatchHistoryRepository;
import com.example.gamematch.session.repository.SessionPlayerRepository;
import com.example.gamematch.session.service.GameSessionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameSessionServiceTest {

    @Mock
    private GameSessionRepository sessionRepository;
    @Mock
    private SessionPlayerRepository sessionPlayerRepository;
    @Mock
    private MatchHistoryRepository matchHistoryRepository;
    @Mock
    private MatchService matchService;
    @Mock
    private PlayerService playerService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private NotificationService notificationService;

    private MatchmakingMetrics metrics;
    private Clock fixedClock;
    private GameSessionService sessionService;

    @BeforeEach
    void setUp() {
        metrics = new MatchmakingMetrics(new SimpleMeterRegistry());
        fixedClock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneId.of("UTC"));
        sessionService = new GameSessionService(
                sessionRepository, sessionPlayerRepository, matchHistoryRepository,
                matchService, playerService, playerRepository, notificationService,
                metrics, fixedClock
        );
    }

    @Test
    @DisplayName("Creates game session and session player rows")
    void createSessionSuccess() {
        Match match = new Match(10L, Region.NA, GameMode.RANKED_1V1, 1200, fixedClock.instant());
        Player p1 = new Player(1L, "P1", Region.NA, 1200, GameMode.RANKED_1V1, fixedClock.instant());
        Player p2 = new Player(2L, "P2", Region.NA, 1200, GameMode.RANKED_1V1, fixedClock.instant());

        GameSession session = new GameSession(100L, 10L, "game-srv-01.match.internal:7777", fixedClock.instant());
        when(sessionRepository.save(any())).thenReturn(session);

        GameSession result = sessionService.createSession(match, List.of(p1, p2));

        assertThat(result.getStatus()).isEqualTo(SessionStatus.WAITING_FOR_PLAYERS);
        verify(sessionPlayerRepository, org.mockito.Mockito.times(2)).save(any(SessionPlayer.class));
        verify(notificationService).broadcastToPlayers(any(), any());
    }

    @Test
    @DisplayName("Transitions session and match to ACTIVE when all players ready up")
    void playerReadyTransitionsToActiveWhenAllReady() {
        GameSession session = new GameSession(10L, "server:7777", fixedClock.instant());
        when(sessionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        SessionPlayer sp1 = new SessionPlayer(1L, 101L, fixedClock.instant());
        sp1.setReady(fixedClock.instant());
        SessionPlayer sp2 = new SessionPlayer(1L, 102L, fixedClock.instant());

        when(sessionPlayerRepository.findBySessionIdAndPlayerIdForUpdate(1L, 102L)).thenReturn(Optional.of(sp2));
        when(sessionPlayerRepository.findBySessionId(1L)).thenReturn(List.of(sp1, sp2));

        SessionResponse response = sessionService.playerReady(1L, 102L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        verify(matchService).updateMatchStatus(10L, MatchStatus.ACTIVE);
        verify(notificationService).broadcastToPlayers(any(), any());
    }

    @Test
    @DisplayName("Records heartbeat and updates last-seen")
    void playerHeartbeatUpdatesLastSeen() {
        GameSession session = new GameSession(10L, "server:7777", fixedClock.instant());
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        SessionPlayer sp1 = new SessionPlayer(1L, 101L, fixedClock.instant());
        when(sessionPlayerRepository.findBySessionIdAndPlayerIdForUpdate(1L, 101L)).thenReturn(Optional.of(sp1));
        when(sessionPlayerRepository.findBySessionId(1L)).thenReturn(List.of(sp1));

        SessionResponse response = sessionService.playerHeartbeat(1L, 101L);

        assertThat(sp1.isConnected()).isTrue();
        assertThat(sp1.getLastHeartbeat()).isEqualTo(fixedClock.instant());
    }

    @Test
    @DisplayName("Detects disconnected player when heartbeat timeout is exceeded")
    void checkHeartbeatsMarksDisconnected() {
        GameSession session = new GameSession(10L, "server:7777", fixedClock.instant().minusSeconds(60));
        when(sessionRepository.findByStatusIn(any())).thenReturn(List.of(session));

        SessionPlayer sp1 = new SessionPlayer(1L, 101L, fixedClock.instant().minusSeconds(30)); // 30s ago > 15s timeout
        when(sessionPlayerRepository.findBySessionId(session.getId())).thenReturn(List.of(sp1));

        sessionService.checkHeartbeats(15L);

        assertThat(sp1.isConnected()).isFalse();
        verify(notificationService).broadcastToPlayers(any(), any());
    }

    @Test
    @DisplayName("Completes session, awards ratings, and writes match history")
    void completeSessionSuccess() {
        GameSession session = new GameSession(10L, "server:7777", fixedClock.instant());
        when(sessionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        SessionPlayer sp1 = new SessionPlayer(1L, 101L, fixedClock.instant());
        SessionPlayer sp2 = new SessionPlayer(1L, 102L, fixedClock.instant());
        when(sessionPlayerRepository.findBySessionId(1L)).thenReturn(List.of(sp1, sp2));

        SessionResponse response = sessionService.completeSession(1L, 101L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        verify(playerService).updateSkillRating(101L, 25);
        verify(playerService).updateSkillRating(102L, -25);
        verify(matchHistoryRepository, org.mockito.Mockito.times(2)).save(any(MatchHistory.class));
        verify(matchService).updateMatchStatus(10L, MatchStatus.COMPLETED);
    }
}
