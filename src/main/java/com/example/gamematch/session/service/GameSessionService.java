package com.example.gamematch.session.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.gamematch.common.exception.ConflictException;
import com.example.gamematch.common.exception.ResourceNotFoundException;
import com.example.gamematch.match.model.Match;
import com.example.gamematch.match.model.MatchStatus;
import com.example.gamematch.match.service.MatchService;
import com.example.gamematch.notification.model.NotificationMessage;
import com.example.gamematch.notification.model.NotificationType;
import com.example.gamematch.notification.service.NotificationService;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.player.model.Player;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameSessionService {

    private static final Logger log = LoggerFactory.getLogger(GameSessionService.class);

    private final GameSessionRepository sessionRepository;
    private final SessionPlayerRepository sessionPlayerRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final MatchService matchService;
    private final PlayerService playerService;
    private final PlayerRepository playerRepository;
    private final NotificationService notificationService;
    private final MatchmakingMetrics metrics;
    private final Clock clock;

    public GameSessionService(GameSessionRepository sessionRepository,
                              SessionPlayerRepository sessionPlayerRepository,
                              MatchHistoryRepository matchHistoryRepository,
                              MatchService matchService,
                              PlayerService playerService,
                              PlayerRepository playerRepository,
                              NotificationService notificationService,
                              MatchmakingMetrics metrics,
                              Clock clock) {
        this.sessionRepository = sessionRepository;
        this.sessionPlayerRepository = sessionPlayerRepository;
        this.matchHistoryRepository = matchHistoryRepository;
        this.matchService = matchService;
        this.playerService = playerService;
        this.playerRepository = playerRepository;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public GameSession createSession(Match match, List<Player> players) {
        Instant now = clock.instant();
        String serverAddress = "game-srv-" + UUID.randomUUID().toString().substring(0, 8) + ".match.internal:7777";

        GameSession session = new GameSession(match.getId(), serverAddress, now);
        GameSession savedSession = sessionRepository.save(session);

        for (Player p : players) {
            SessionPlayer sp = new SessionPlayer(savedSession.getId(), p.getId(), now);
            sessionPlayerRepository.save(sp);
        }

        updateActiveSessionsCount();

        // Notify players that match was found and session is ready for check-in
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        NotificationMessage msg = NotificationMessage.of(
                NotificationType.MATCH_FOUND,
                null,
                match.getId(),
                savedSession.getId(),
                "Match found! Please ready up for session " + savedSession.getId(),
                Map.of("serverAddress", serverAddress, "matchId", match.getId())
        );
        notificationService.broadcastToPlayers(playerIds, msg);

        log.info("Created game session id={} for match id={} with players {}",
                savedSession.getId(), match.getId(), playerIds);
        return savedSession;
    }

    @Transactional
    public SessionResponse playerReady(Long sessionId, Long playerId) {
        GameSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.CANCELLED) {
            throw new ConflictException("Cannot ready up on finished session: " + sessionId);
        }

        SessionPlayer sessionPlayer = sessionPlayerRepository.findBySessionIdAndPlayerIdForUpdate(sessionId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player " + playerId + " not in session " + sessionId));

        Instant now = clock.instant();
        sessionPlayer.setReady(now);
        sessionPlayerRepository.save(sessionPlayer);

        List<SessionPlayer> allPlayers = sessionPlayerRepository.findBySessionId(sessionId);
        boolean allReady = allPlayers.stream().allMatch(SessionPlayer::isReady);

        if (allReady && session.getStatus() == SessionStatus.WAITING_FOR_PLAYERS) {
            session.updateStatus(SessionStatus.ACTIVE, now);
            sessionRepository.save(session);
            matchService.updateMatchStatus(session.getMatchId(), MatchStatus.ACTIVE);

            List<Long> playerIds = allPlayers.stream().map(SessionPlayer::getPlayerId).toList();
            NotificationMessage readyMsg = NotificationMessage.of(
                    NotificationType.SESSION_STATE_CHANGED,
                    null,
                    session.getMatchId(),
                    sessionId,
                    "All players are ready! Match is now ACTIVE.",
                    Map.of("status", SessionStatus.ACTIVE)
            );
            notificationService.broadcastToPlayers(playerIds, readyMsg);
        } else {
            notificationService.sendNotification(playerId, NotificationMessage.of(
                    NotificationType.PLAYER_READY,
                    playerId,
                    session.getMatchId(),
                    sessionId,
                    "Ready state confirmed",
                    Map.of("ready", true)
            ));
        }

        return getSessionResponse(sessionId);
    }

    @Transactional
    public SessionResponse playerHeartbeat(Long sessionId, Long playerId) {
        GameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        SessionPlayer sessionPlayer = sessionPlayerRepository.findBySessionIdAndPlayerIdForUpdate(sessionId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player " + playerId + " not in session " + sessionId));

        Instant now = clock.instant();
        sessionPlayer.recordHeartbeat(now);
        sessionPlayerRepository.save(sessionPlayer);

        return getSessionResponse(sessionId);
    }

    @Transactional
    public SessionResponse completeSession(Long sessionId, Long winnerPlayerId) {
        GameSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.CANCELLED) {
            throw new ConflictException("Session " + sessionId + " is already ended with status " + session.getStatus());
        }

        Instant now = clock.instant();
        session.updateStatus(SessionStatus.COMPLETED, now);
        sessionRepository.save(session);
        matchService.updateMatchStatus(session.getMatchId(), MatchStatus.COMPLETED);

        List<SessionPlayer> roster = sessionPlayerRepository.findBySessionId(sessionId);
        for (SessionPlayer sp : roster) {
            boolean isWinner = sp.getPlayerId().equals(winnerPlayerId);
            int ratingDelta = isWinner ? 25 : -25;
            String outcome = isWinner ? "WON" : "LOST";

            playerService.updateSkillRating(sp.getPlayerId(), ratingDelta);
            matchHistoryRepository.save(new MatchHistory(session.getMatchId(), sp.getPlayerId(), outcome, ratingDelta, now));
        }

        updateActiveSessionsCount();

        List<Long> playerIds = roster.stream().map(SessionPlayer::getPlayerId).toList();
        NotificationMessage completedMsg = NotificationMessage.of(
                NotificationType.SESSION_COMPLETED,
                null,
                session.getMatchId(),
                sessionId,
                "Game session completed. Winner: " + winnerPlayerId,
                Map.of("winnerPlayerId", winnerPlayerId)
        );
        notificationService.broadcastToPlayers(playerIds, completedMsg);

        log.info("Completed session id={} winner={}", sessionId, winnerPlayerId);
        return getSessionResponse(sessionId);
    }

    @Transactional
    public SessionResponse cancelSession(Long sessionId, String reason) {
        GameSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.CANCELLED) {
            throw new ConflictException("Session " + sessionId + " is already ended with status " + session.getStatus());
        }

        Instant now = clock.instant();
        session.updateStatus(SessionStatus.CANCELLED, now);
        sessionRepository.save(session);
        matchService.updateMatchStatus(session.getMatchId(), MatchStatus.CANCELLED);

        List<SessionPlayer> roster = sessionPlayerRepository.findBySessionId(sessionId);
        for (SessionPlayer sp : roster) {
            matchHistoryRepository.save(new MatchHistory(session.getMatchId(), sp.getPlayerId(), "CANCELLED", 0, now));
        }

        updateActiveSessionsCount();

        List<Long> playerIds = roster.stream().map(SessionPlayer::getPlayerId).toList();
        NotificationMessage cancelMsg = NotificationMessage.of(
                NotificationType.SESSION_CANCELLED,
                null,
                session.getMatchId(),
                sessionId,
                "Game session cancelled. Reason: " + (reason != null ? reason : "Unknown"),
                Map.of("reason", reason != null ? reason : "Unknown")
        );
        notificationService.broadcastToPlayers(playerIds, cancelMsg);

        log.warn("Cancelled session id={} reason={}", sessionId, reason);
        return getSessionResponse(sessionId);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSessionResponse(Long sessionId) {
        GameSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        List<SessionPlayer> roster = sessionPlayerRepository.findBySessionId(sessionId);
        List<Long> playerIds = roster.stream().map(SessionPlayer::getPlayerId).toList();
        Map<Long, String> names = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, Player::getDisplayName));

        List<SessionResponse.SessionPlayerDto> playerDtos = roster.stream()
                .map(sp -> new SessionResponse.SessionPlayerDto(
                        sp.getPlayerId(),
                        names.getOrDefault(sp.getPlayerId(), "Unknown"),
                        sp.isReady(),
                        sp.isConnected(),
                        sp.getLastHeartbeat()))
                .toList();

        return SessionResponse.from(session, playerDtos);
    }

    @Transactional(readOnly = true)
    public List<MatchHistory> getPlayerHistory(Long playerId) {
        return matchHistoryRepository.findByPlayerIdOrderByRecordedAtDesc(playerId);
    }

    @Transactional
    public void checkHeartbeats(long timeoutSeconds) {
        Instant threshold = clock.instant().minusSeconds(timeoutSeconds);
        List<GameSession> activeSessions = sessionRepository.findByStatusIn(
                List.of(SessionStatus.WAITING_FOR_PLAYERS, SessionStatus.READY, SessionStatus.ACTIVE));

        for (GameSession session : activeSessions) {
            List<SessionPlayer> players = sessionPlayerRepository.findBySessionId(session.getId());
            for (SessionPlayer sp : players) {
                if (sp.isConnected() && sp.getLastHeartbeat().isBefore(threshold)) {
                    sp.markDisconnected(clock.instant());
                    sessionPlayerRepository.save(sp);
                    metrics.incrementDisconnectedPlayers();

                    log.warn("Player {} disconnected in session {} (last heartbeat: {})",
                            sp.getPlayerId(), session.getId(), sp.getLastHeartbeat());

                    notificationService.broadcastToPlayers(
                            players.stream().map(SessionPlayer::getPlayerId).toList(),
                            NotificationMessage.of(
                                    NotificationType.PLAYER_DISCONNECTED,
                                    sp.getPlayerId(),
                                    session.getMatchId(),
                                    session.getId(),
                                    "Player " + sp.getPlayerId() + " disconnected due to missing heartbeat",
                                    Map.of("disconnectedPlayerId", sp.getPlayerId())
                            )
                    );
                }
            }
        }
    }

    private void updateActiveSessionsCount() {
        long count = sessionRepository.countByStatusIn(
                List.of(SessionStatus.WAITING_FOR_PLAYERS, SessionStatus.READY, SessionStatus.ACTIVE));
        metrics.setActiveSessions((int) count);
    }
}
