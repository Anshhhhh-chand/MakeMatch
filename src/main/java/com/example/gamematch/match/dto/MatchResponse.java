package com.example.gamematch.match.dto;

import java.time.Instant;
import java.util.List;

import com.example.gamematch.match.model.Match;
import com.example.gamematch.match.model.MatchStatus;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;

public record MatchResponse(
        Long id,
        Region region,
        GameMode gameMode,
        MatchStatus status,
        int averageRating,
        Instant createdAt,
        Instant completedAt,
        List<MatchPlayerDto> players
) {
    public record MatchPlayerDto(
            Long playerId,
            String displayName,
            int skillRating,
            int slotNumber
    ) {}

    public static MatchResponse from(Match match, List<MatchPlayerDto> players) {
        return new MatchResponse(
                match.getId(),
                match.getRegion(),
                match.getGameMode(),
                match.getStatus(),
                match.getAverageRating(),
                match.getCreatedAt(),
                match.getCompletedAt(),
                players
        );
    }
}
