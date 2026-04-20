package com.example.gamematch.matchmaking.dto;

import java.time.Instant;

import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;

public record QueueStatusResponse(
        Long playerId,
        boolean inQueue,
        Region region,
        GameMode mode,
        int skillRating,
        long queueSizeForMode,
        long totalQueueSize,
        Instant joinedAt
) {}
