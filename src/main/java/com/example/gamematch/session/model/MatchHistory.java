package com.example.gamematch.session.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "match_history")
public class MatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(length = 20)
    private String outcome;

    @Column(name = "rating_change", nullable = false)
    private int ratingChange;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected MatchHistory() {
    }

    public MatchHistory(Long matchId, Long playerId, String outcome, int ratingChange, Instant recordedAt) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.outcome = outcome;
        this.ratingChange = ratingChange;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMatchId() {
        return matchId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public String getOutcome() {
        return outcome;
    }

    public int getRatingChange() {
        return ratingChange;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
