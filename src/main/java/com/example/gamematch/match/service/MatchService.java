package com.example.gamematch.match.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.gamematch.common.exception.ResourceNotFoundException;
import com.example.gamematch.match.dto.MatchResponse;
import com.example.gamematch.match.model.Match;
import com.example.gamematch.match.model.MatchPlayer;
import com.example.gamematch.match.model.MatchStatus;
import com.example.gamematch.match.repository.MatchPlayerRepository;
import com.example.gamematch.match.repository.MatchRepository;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final PlayerRepository playerRepository;
    private final Clock clock;

    public MatchService(MatchRepository matchRepository,
                        MatchPlayerRepository matchPlayerRepository,
                        PlayerRepository playerRepository,
                        Clock clock) {
        this.matchRepository = matchRepository;
        this.matchPlayerRepository = matchPlayerRepository;
        this.playerRepository = playerRepository;
        this.clock = clock;
    }

    @Transactional
    public Match createMatch(Region region, GameMode gameMode, List<Player> players) {
        Instant now = clock.instant();
        int avgRating = (int) players.stream().mapToInt(Player::getSkillRating).average().orElse(1000);

        Match match = new Match(region, gameMode, avgRating, now);
        Match savedMatch = matchRepository.save(match);

        int slot = 1;
        for (Player p : players) {
            MatchPlayer mp = new MatchPlayer(savedMatch.getId(), p.getId(), p.getSkillRating(), slot++, now);
            matchPlayerRepository.save(mp);
        }

        log.info("Created match id={} region={} mode={} avgRating={} players={}",
                savedMatch.getId(), region, gameMode, avgRating,
                players.stream().map(Player::getId).toList());
        return savedMatch;
    }

    @Transactional(readOnly = true)
    public Match getMatch(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found with id: " + matchId));
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatchResponse(Long matchId) {
        Match match = getMatch(matchId);
        List<MatchPlayer> roster = matchPlayerRepository.findByMatchIdOrderBySlotNumber(matchId);

        List<Long> playerIds = roster.stream().map(MatchPlayer::getPlayerId).toList();
        Map<Long, String> playerNames = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, Player::getDisplayName));

        List<MatchResponse.MatchPlayerDto> playerDtos = roster.stream()
                .map(mp -> new MatchResponse.MatchPlayerDto(
                        mp.getPlayerId(),
                        playerNames.getOrDefault(mp.getPlayerId(), "Unknown"),
                        mp.getSkillRating(),
                        mp.getSlotNumber()))
                .toList();

        return MatchResponse.from(match, playerDtos);
    }

    @Transactional
    public void updateMatchStatus(Long matchId, MatchStatus newStatus) {
        Match match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found with id: " + matchId));
        match.transitionTo(newStatus, clock.instant());
        matchRepository.save(match);
        log.info("Match id={} transitioned to status {}", matchId, newStatus);
    }
}
