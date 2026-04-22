package com.example.gamematch.session.repository;

import java.util.List;

import com.example.gamematch.session.model.MatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {

    List<MatchHistory> findByPlayerIdOrderByRecordedAtDesc(Long playerId);

    List<MatchHistory> findByMatchId(Long matchId);
}
