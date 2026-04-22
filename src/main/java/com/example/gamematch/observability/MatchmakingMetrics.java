package com.example.gamematch.observability;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class MatchmakingMetrics {

    private final Counter matchmakingAttempts;
    private final Counter successfulMatches;
    private final Counter failedMatches;
    private final Counter disconnectedPlayers;
    private final Counter apiErrorCount;
    private final Timer matchmakingDuration;

    private final AtomicLong currentQueueSize = new AtomicLong(0);
    private final AtomicInteger activeSessionsCount = new AtomicInteger(0);

    public MatchmakingMetrics(MeterRegistry meterRegistry) {
        this.matchmakingAttempts = Counter.builder("gamematch.matchmaking.attempts")
                .description("Total matchmaking attempts")
                .register(meterRegistry);

        this.successfulMatches = Counter.builder("gamematch.matches.success")
                .description("Total successful matches formed")
                .register(meterRegistry);

        this.failedMatches = Counter.builder("gamematch.matches.failed")
                .description("Total failed matchmaking attempts")
                .register(meterRegistry);

        this.disconnectedPlayers = Counter.builder("gamematch.players.disconnected")
                .description("Total disconnected players detected")
                .register(meterRegistry);

        this.apiErrorCount = Counter.builder("gamematch.api.errors")
                .description("Total API errors handled")
                .register(meterRegistry);

        this.matchmakingDuration = Timer.builder("gamematch.matchmaking.time")
                .description("Time taken to evaluate and form a match")
                .register(meterRegistry);

        meterRegistry.gauge("gamematch.queue.size", currentQueueSize);
        meterRegistry.gauge("gamematch.sessions.active", activeSessionsCount);
    }

    public void incrementAttempts() {
        matchmakingAttempts.increment();
    }

    public void incrementSuccessfulMatches() {
        successfulMatches.increment();
    }

    public void incrementFailedMatches() {
        failedMatches.increment();
    }

    public void incrementDisconnectedPlayers() {
        disconnectedPlayers.increment();
    }

    public void incrementApiErrors() {
        apiErrorCount.increment();
    }

    public Timer getMatchmakingTimer() {
        return matchmakingDuration;
    }

    public void setQueueSize(long size) {
        currentQueueSize.set(size);
    }

    public void setActiveSessions(int count) {
        activeSessionsCount.set(count);
    }

    public long getQueueSize() {
        return currentQueueSize.get();
    }

    public int getActiveSessions() {
        return activeSessionsCount.get();
    }
}
