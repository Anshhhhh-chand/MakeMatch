package com.example.gamematch.session.dto;

import jakarta.validation.constraints.NotNull;

public record CompleteSessionRequest(
        @NotNull(message = "Winner player ID is required")
        Long winnerPlayerId
) {}
