package com.example.gamematch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gamematch")
public record MatchmakingProperties(
        Matchmaking matchmaking,
        Session session
) {
    public record Matchmaking(
            int ratingTolerance,
            int defaultTeamSize,
            long evaluationIntervalMs
    ) {
        public Matchmaking {
            if (ratingTolerance <= 0) ratingTolerance = 150;
            if (defaultTeamSize <= 0) defaultTeamSize = 1;
            if (evaluationIntervalMs <= 0) evaluationIntervalMs = 2000L;
        }
    }

    public record Session(
            long heartbeatTimeoutSeconds,
            long heartbeatCheckIntervalSeconds
    ) {
        public Session {
            if (heartbeatTimeoutSeconds <= 0) heartbeatTimeoutSeconds = 15L;
            if (heartbeatCheckIntervalSeconds <= 0) heartbeatCheckIntervalSeconds = 5L;
        }
    }
}
