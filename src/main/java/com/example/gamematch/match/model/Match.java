package com.example.gamematch.match.model;

import java.time.Instant;

import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false, length = 30)
    private GameMode gameMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status;

    @Column(name = "average_rating", nullable = false)
    private int averageRating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Match() {
    }

    public Match(Region region, GameMode gameMode, int averageRating, Instant now) {
        this(null, region, gameMode, averageRating, now);
    }

    public Match(Long id, Region region, GameMode gameMode, int averageRating, Instant now) {
        this.id = id;
        this.region = region;
        this.gameMode = gameMode;
        this.averageRating = averageRating;
        this.status = MatchStatus.WAITING;
        this.createdAt = now;
    }

    public void transitionTo(MatchStatus newStatus, Instant now) {
        this.status = newStatus;
        if (newStatus == MatchStatus.COMPLETED || newStatus == MatchStatus.CANCELLED) {
            this.completedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Region getRegion() {
        return region;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public int getAverageRating() {
        return averageRating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
