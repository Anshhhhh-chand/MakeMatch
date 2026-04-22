package com.example.gamematch.notification.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.gamematch.notification.model.NotificationMessage;
import com.example.gamematch.notification.model.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final ObjectMapper objectMapper;
    private final Map<Long, List<SseEmitter>> sseEmitters = new ConcurrentHashMap<>();
    private final Map<Long, List<WebSocketSession>> webSocketSessions = new ConcurrentHashMap<>();
    private final Map<Long, List<NotificationMessage>> messageHistory = new ConcurrentHashMap<>();

    public NotificationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribeSse(Long playerId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        sseEmitters.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeSseEmitter(playerId, emitter));
        emitter.onTimeout(() -> removeSseEmitter(playerId, emitter));
        emitter.onError(e -> removeSseEmitter(playerId, emitter));

        try {
            // Initial greeting / handshake
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(NotificationMessage.of(NotificationType.CONNECTED, playerId, null, null, "SSE stream connected", null)));

            // Replay last unread / recent notifications if reconnecting
            List<NotificationMessage> recent = messageHistory.get(playerId);
            if (recent != null && !recent.isEmpty()) {
                for (NotificationMessage msg : recent) {
                    emitter.send(SseEmitter.event().name(msg.type().name()).data(msg));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to send initial SSE connection event to player {}: {}", playerId, e.getMessage());
            removeSseEmitter(playerId, emitter);
        }

        log.info("Player {} subscribed to SSE notifications", playerId);
        return emitter;
    }

    public void registerWebSocketSession(Long playerId, WebSocketSession session) {
        webSocketSessions.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>()).add(session);
        log.info("Registered WebSocket session for player {}", playerId);

        // Send initial connected message
        try {
            NotificationMessage msg = NotificationMessage.of(NotificationType.CONNECTED, playerId, null, null, "WebSocket connected", null);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (IOException e) {
            log.warn("Failed to send initial WebSocket message to player {}: {}", playerId, e.getMessage());
        }
    }

    public void unregisterWebSocketSession(Long playerId, WebSocketSession session) {
        List<WebSocketSession> sessions = webSocketSessions.get(playerId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                webSocketSessions.remove(playerId);
            }
        }
    }

    public void sendNotification(Long playerId, NotificationMessage message) {
        // Record in history for graceful reconnects (bounded to last 10 messages)
        messageHistory.compute(playerId, (k, list) -> {
            if (list == null) list = new CopyOnWriteArrayList<>();
            list.add(message);
            while (list.size() > 10) {
                list.remove(0);
            }
            return list;
        });

        // 1. Deliver to SSE subscribers
        List<SseEmitter> emitters = sseEmitters.get(playerId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(message.type().name())
                            .data(message));
                } catch (Exception e) {
                    log.warn("Failed to deliver SSE notification to player {}: {}", playerId, e.getMessage());
                    removeSseEmitter(playerId, emitter);
                }
            }
        }

        // 2. Deliver to WebSocket subscribers
        List<WebSocketSession> wsList = webSocketSessions.get(playerId);
        if (wsList != null) {
            try {
                String payload = objectMapper.writeValueAsString(message);
                TextMessage textMessage = new TextMessage(payload);
                for (WebSocketSession session : wsList) {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to deliver WebSocket notification to player {}: {}", playerId, e.getMessage());
            }
        }

        log.debug("Sent notification {} to player {}", message.type(), playerId);
    }

    public void broadcastToPlayers(List<Long> playerIds, NotificationMessage message) {
        for (Long playerId : playerIds) {
            sendNotification(playerId, message);
        }
    }

    private void removeSseEmitter(Long playerId, SseEmitter emitter) {
        List<SseEmitter> emitters = sseEmitters.get(playerId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                sseEmitters.remove(playerId);
            }
        }
    }
}
