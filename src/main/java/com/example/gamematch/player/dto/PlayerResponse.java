package com.example.gamematch.player.dto;

import java.time.Instant;

import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;

public record PlayerResponse(
        Long id,
        String displayName,
        Region region,
        int skillRating,
        GameMode preferredMode,
        Instant createdAt,
        Instant updatedAt
) {
    public static PlayerResponse from(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getDisplayName(),
                player.getRegion(),
                player.getSkillRating(),
                player.getPreferredMode(),
                player.getCreatedAt(),
                player.getUpdatedAt()
        );
    }
}
