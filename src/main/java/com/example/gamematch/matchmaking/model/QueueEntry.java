package com.example.gamematch.matchmaking.model;

import java.time.Instant;

import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;

public record QueueEntry(
        Long playerId,
        Region region,
        GameMode mode,
        int skillRating,
        Instant joinedAt
) {}
