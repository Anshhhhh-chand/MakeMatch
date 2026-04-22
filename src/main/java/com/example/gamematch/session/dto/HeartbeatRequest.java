package com.example.gamematch.session.dto;

import jakarta.validation.constraints.NotNull;

public record HeartbeatRequest(
        @NotNull(message = "Player ID is required")
        Long playerId
) {}
