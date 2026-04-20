package com.example.gamematch.matchmaking.dto;

import jakarta.validation.constraints.NotNull;

public record LeaveQueueRequest(
        @NotNull(message = "Player ID is required")
        Long playerId
) {}
