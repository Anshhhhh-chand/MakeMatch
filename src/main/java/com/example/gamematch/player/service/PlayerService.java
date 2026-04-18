package com.example.gamematch.player.service;

import java.time.Clock;
import java.time.Instant;

import com.example.gamematch.common.exception.ConflictException;
import com.example.gamematch.common.exception.ResourceNotFoundException;
import com.example.gamematch.player.dto.CreatePlayerRequest;
import com.example.gamematch.player.dto.PlayerResponse;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerRepository playerRepository;
    private final Clock clock;

    public PlayerService(PlayerRepository playerRepository, Clock clock) {
        this.playerRepository = playerRepository;
        this.clock = clock;
    }

    @Transactional
    public PlayerResponse createPlayer(CreatePlayerRequest request) {
        if (playerRepository.existsByDisplayName(request.displayName())) {
            throw new ConflictException("Player with display name '" + request.displayName() + "' already exists");
        }

        Instant now = clock.instant();
        Player player = new Player(
                request.displayName(),
                request.region(),
                request.getEffectiveSkillRating(),
                request.preferredMode(),
                now
        );

        Player saved = playerRepository.save(player);
        log.info("Created player id={} displayName='{}' region={} rating={}",
                saved.getId(), saved.getDisplayName(), saved.getRegion(), saved.getSkillRating());
        return PlayerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayer(Long id) {
        Player player = getPlayerEntity(id);
        return PlayerResponse.from(player);
    }

    @Transactional(readOnly = true)
    public Player getPlayerEntity(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found with id: " + id));
    }

    @Transactional
    public Player updateSkillRating(Long id, int delta) {
        Player player = playerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found with id: " + id));
        player.updateSkillRating(delta, clock.instant());
        return playerRepository.save(player);
    }
}
