package com.example.gamematch.match.repository;

import java.util.List;

import com.example.gamematch.match.model.MatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, Long> {

    List<MatchPlayer> findByMatchIdOrderBySlotNumber(Long matchId);

    List<MatchPlayer> findByPlayerId(Long playerId);
}
