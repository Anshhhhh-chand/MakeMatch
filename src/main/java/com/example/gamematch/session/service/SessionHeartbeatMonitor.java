package com.example.gamematch.session.service;

import com.example.gamematch.config.MatchmakingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionHeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(SessionHeartbeatMonitor.class);

    private final GameSessionService sessionService;
    private final MatchmakingProperties properties;

    public SessionHeartbeatMonitor(GameSessionService sessionService, MatchmakingProperties properties) {
        this.sessionService = sessionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${gamematch.session.heartbeat-check-interval-seconds:5}000")
    public void monitorHeartbeats() {
        try {
            long timeout = properties.session().heartbeatTimeoutSeconds();
            sessionService.checkHeartbeats(timeout);
        } catch (Exception e) {
            log.error("Error occurred during heartbeat check cycle: ", e);
        }
    }
}
