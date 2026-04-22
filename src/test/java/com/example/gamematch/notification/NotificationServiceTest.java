package com.example.gamematch.notification;

import java.util.Map;

import com.example.gamematch.notification.model.NotificationMessage;
import com.example.gamematch.notification.model.NotificationType;
import com.example.gamematch.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        notificationService = new NotificationService(objectMapper);
    }

    @Test
    @DisplayName("Subscribes player to SSE and delivers notifications")
    void sseSubscribeAndSend() {
        SseEmitter emitter = notificationService.subscribeSse(101L);
        assertThat(emitter).isNotNull();

        NotificationMessage message = NotificationMessage.of(
                NotificationType.MATCH_FOUND,
                101L,
                1L,
                10L,
                "Match ready",
                Map.of("matchId", 1L)
        );

        // Sending should not throw
        notificationService.sendNotification(101L, message);
    }
}
