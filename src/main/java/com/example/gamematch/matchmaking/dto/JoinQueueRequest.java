package com.example.gamematch.matchmaking.dto;

import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;
import jakarta.validation.constraints.NotNull;

public record JoinQueueRequest(
        @NotNull(message = "Player ID is required")
        Long playerId,

        Region region,

        GameMode mode
) {}
