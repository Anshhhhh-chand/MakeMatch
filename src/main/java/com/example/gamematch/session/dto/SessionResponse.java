package com.example.gamematch.session.dto;

import java.time.Instant;
import java.util.List;

import com.example.gamematch.session.model.GameSession;
import com.example.gamematch.session.model.SessionStatus;

public record SessionResponse(
        Long id,
        Long matchId,
        SessionStatus status,
        String serverAddress,
        Instant createdAt,
        Instant endedAt,
        List<SessionPlayerDto> players
) {
    public record SessionPlayerDto(
            Long playerId,
            String displayName,
            boolean ready,
            boolean connected,
            Instant lastHeartbeat
    ) {}

    public static SessionResponse from(GameSession session, List<SessionPlayerDto> players) {
        return new SessionResponse(
                session.getId(),
                session.getMatchId(),
                session.getStatus(),
                session.getServerAddress(),
                session.getCreatedAt(),
                session.getEndedAt(),
                players
        );
    }
}
