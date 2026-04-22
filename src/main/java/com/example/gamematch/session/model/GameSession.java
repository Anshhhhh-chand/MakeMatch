package com.example.gamematch.session.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_sessions")
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, unique = true)
    private Long matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SessionStatus status;

    @Column(name = "server_address", length = 100)
    private String serverAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected GameSession() {
    }

    public GameSession(Long matchId, String serverAddress, Instant now) {
        this(null, matchId, serverAddress, now);
    }

    public GameSession(Long id, Long matchId, String serverAddress, Instant now) {
        this.id = id;
        this.matchId = matchId;
        this.status = SessionStatus.WAITING_FOR_PLAYERS;
        this.serverAddress = serverAddress;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateStatus(SessionStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
        if (newStatus == SessionStatus.COMPLETED || newStatus == SessionStatus.CANCELLED) {
            this.endedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getMatchId() {
        return matchId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
