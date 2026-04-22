package com.example.gamematch.session.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "session_players")
public class SessionPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "is_ready", nullable = false)
    private boolean isReady;

    @Column(name = "is_connected", nullable = false)
    private boolean isConnected;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    protected SessionPlayer() {
    }

    public SessionPlayer(Long sessionId, Long playerId, Instant now) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.isReady = false;
        this.isConnected = true;
        this.lastHeartbeat = now;
    }

    public void setReady(Instant now) {
        this.isReady = true;
        this.readyAt = now;
        this.lastHeartbeat = now;
        this.isConnected = true;
    }

    public void recordHeartbeat(Instant now) {
        this.lastHeartbeat = now;
        this.isConnected = true;
    }

    public void markDisconnected(Instant now) {
        this.isConnected = false;
        this.disconnectedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public boolean isReady() {
        return isReady;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getDisconnectedAt() {
        return disconnectedAt;
    }
}
