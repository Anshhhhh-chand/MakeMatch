package com.example.gamematch.config;

import java.net.URI;

import com.example.gamematch.notification.service.NotificationService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationService notificationService;

    public WebSocketConfig(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MatchWebSocketHandler(notificationService), "/ws/notifications")
                .setAllowedOrigins("*");
    }

    private static class MatchWebSocketHandler extends TextWebSocketHandler {
        private final NotificationService notificationService;

        public MatchWebSocketHandler(NotificationService notificationService) {
            this.notificationService = notificationService;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            Long playerId = extractPlayerId(session);
            if (playerId != null) {
                notificationService.registerWebSocketSession(playerId, session);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            Long playerId = extractPlayerId(session);
            if (playerId != null) {
                notificationService.unregisterWebSocketSession(playerId, session);
            }
        }

        private Long extractPlayerId(WebSocketSession session) {
            URI uri = session.getUri();
            if (uri != null && uri.getQuery() != null) {
                for (String param : uri.getQuery().split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "playerId".equalsIgnoreCase(pair[0])) {
                        try {
                            return Long.parseLong(pair[1]);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            return null;
        }
    }
}
