package com.example.gamematch.player;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.example.gamematch.common.exception.ConflictException;
import com.example.gamematch.common.exception.ResourceNotFoundException;
import com.example.gamematch.player.dto.CreatePlayerRequest;
import com.example.gamematch.player.dto.PlayerResponse;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.repository.PlayerRepository;
import com.example.gamematch.player.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    private Clock fixedClock;
    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneId.of("UTC"));
        playerService = new PlayerService(playerRepository, fixedClock);
    }

    @Test
    @DisplayName("Successfully creates player profile")
    void createPlayerSuccess() {
        CreatePlayerRequest request = new CreatePlayerRequest("AceGamer", Region.NA, 1200, GameMode.RANKED_1V1);
        when(playerRepository.existsByDisplayName("AceGamer")).thenReturn(false);

        Player saved = new Player("AceGamer", Region.NA, 1200, GameMode.RANKED_1V1, fixedClock.instant());
        when(playerRepository.save(any(Player.class))).thenReturn(saved);

        PlayerResponse response = playerService.createPlayer(request);

        assertThat(response.displayName()).isEqualTo("AceGamer");
        assertThat(response.region()).isEqualTo(Region.NA);
        assertThat(response.skillRating()).isEqualTo(1200);
        assertThat(response.preferredMode()).isEqualTo(GameMode.RANKED_1V1);
        verify(playerRepository).save(any(Player.class));
    }

    @Test
    @DisplayName("Throws ConflictException when display name already exists")
    void createPlayerDuplicateNameThrowsConflict() {
        CreatePlayerRequest request = new CreatePlayerRequest("AceGamer", Region.NA, 1200, GameMode.RANKED_1V1);
        when(playerRepository.existsByDisplayName("AceGamer")).thenReturn(true);

        assertThatThrownBy(() -> playerService.createPlayer(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when player ID does not exist")
    void getPlayerNotFoundThrowsException() {
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.getPlayer(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player not found");
    }

    @Test
    @DisplayName("Updates skill rating correctly")
    void updateSkillRating() {
        Player player = new Player("AceGamer", Region.NA, 1200, GameMode.RANKED_1V1, fixedClock.instant());
        when(playerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(player));
        when(playerRepository.save(player)).thenReturn(player);

        Player updated = playerService.updateSkillRating(1L, 25);

        assertThat(updated.getSkillRating()).isEqualTo(1225);
    }
}
