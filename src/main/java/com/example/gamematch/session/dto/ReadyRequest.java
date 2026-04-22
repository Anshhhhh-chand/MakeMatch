package com.example.gamematch.session.dto;

import jakarta.validation.constraints.NotNull;

public record ReadyRequest(
        @NotNull(message = "Player ID is required")
        Long playerId
) {}
