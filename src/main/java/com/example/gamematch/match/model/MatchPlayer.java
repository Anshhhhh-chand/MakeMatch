package com.example.gamematch.match.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "match_players")
public class MatchPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "skill_rating", nullable = false)
    private int skillRating;

    @Column(name = "slot_number", nullable = false)
    private int slotNumber;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected MatchPlayer() {
    }

    public MatchPlayer(Long matchId, Long playerId, int skillRating, int slotNumber, Instant joinedAt) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.skillRating = skillRating;
        this.slotNumber = slotNumber;
        this.joinedAt = joinedAt;
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

    public int getSkillRating() {
        return skillRating;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
