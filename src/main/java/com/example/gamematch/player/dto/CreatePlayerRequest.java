package com.example.gamematch.player.dto;

import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePlayerRequest(
        @NotBlank(message = "Display name is required")
        @Size(min = 3, max = 50, message = "Display name must be between 3 and 50 characters")
        String displayName,

        @NotNull(message = "Region is required")
        Region region,

        @Min(value = 0, message = "Skill rating must be non-negative")
        Integer skillRating,

        @NotNull(message = "Preferred game mode is required")
        GameMode preferredMode
) {
    public int getEffectiveSkillRating() {
        return skillRating != null ? skillRating : 1000;
    }
}
