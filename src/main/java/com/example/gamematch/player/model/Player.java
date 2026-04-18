package com.example.gamematch.player.model;

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
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, unique = true, length = 50)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region;

    @Column(name = "skill_rating", nullable = false)
    private int skillRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_mode", nullable = false, length = 30)
    private GameMode preferredMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Player() {
    }

    public Player(String displayName, Region region, int skillRating, GameMode preferredMode, Instant now) {
        this(null, displayName, region, skillRating, preferredMode, now);
    }

    public Player(Long id, String displayName, Region region, int skillRating, GameMode preferredMode, Instant now) {
        this.id = id;
        this.displayName = displayName;
        this.region = region;
        this.skillRating = Math.max(0, skillRating);
        this.preferredMode = preferredMode;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateSkillRating(int delta, Instant now) {
        this.skillRating = Math.max(0, this.skillRating + delta);
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Region getRegion() {
        return region;
    }

    public int getSkillRating() {
        return skillRating;
    }

    public GameMode getPreferredMode() {
        return preferredMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
