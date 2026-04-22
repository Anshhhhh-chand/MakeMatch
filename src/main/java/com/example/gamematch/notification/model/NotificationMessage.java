package com.example.gamematch.notification.model;

import java.time.Instant;

public record NotificationMessage(
        NotificationType type,
        Long playerId,
        Long matchId,
        Long sessionId,
        String message,
        Instant timestamp,
        Object payload
) {
    public static NotificationMessage of(NotificationType type, Long playerId, Long matchId, Long sessionId, String message, Object payload) {
        return new NotificationMessage(type, playerId, matchId, sessionId, message, Instant.now(), payload);
    }
}
