package com.example.gamematch.match;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.example.gamematch.match.dto.MatchResponse;
import com.example.gamematch.match.model.Match;
import com.example.gamematch.match.model.MatchPlayer;
import com.example.gamematch.match.model.MatchStatus;
import com.example.gamematch.match.repository.MatchPlayerRepository;
import com.example.gamematch.match.repository.MatchRepository;
import com.example.gamematch.match.service.MatchService;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchPlayerRepository matchPlayerRepository;
    @Mock
    private PlayerRepository playerRepository;

    private Clock fixedClock;
    private MatchService matchService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneId.of("UTC"));
        matchService = new MatchService(matchRepository, matchPlayerRepository, playerRepository, fixedClock);
    }

    @Test
    @DisplayName("Creates match with participants and calculates average rating")
    void createMatchSuccess() {
        Region region = Region.NA;
        GameMode mode = GameMode.RANKED_1V1;

        Player p1 = new Player("PlayerOne", region, 1200, mode, fixedClock.instant());
        Player p2 = new Player("PlayerTwo", region, 1300, mode, fixedClock.instant());

        Match match = new Match(region, mode, 1250, fixedClock.instant());
        when(matchRepository.save(any(Match.class))).thenReturn(match);

        Match result = matchService.createMatch(region, mode, List.of(p1, p2));

        assertThat(result.getAverageRating()).isEqualTo(1250);
        assertThat(result.getStatus()).isEqualTo(MatchStatus.WAITING);
        verify(matchRepository).save(any(Match.class));
        verify(matchPlayerRepository, org.mockito.Mockito.times(2)).save(any(MatchPlayer.class));
    }

    @Test
    @DisplayName("Updates match status successfully")
    void updateMatchStatus() {
        Match match = new Match(Region.NA, GameMode.RANKED_1V1, 1200, fixedClock.instant());
        when(matchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);

        matchService.updateMatchStatus(1L, MatchStatus.ACTIVE);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.ACTIVE);
    }
}
