package com.example.gamematch.session.repository;

import java.util.List;
import java.util.Optional;

import com.example.gamematch.session.model.GameSession;
import com.example.gamematch.session.model.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findByMatchId(Long matchId);

    List<GameSession> findByStatus(SessionStatus status);

    List<GameSession> findByStatusIn(List<SessionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM GameSession s WHERE s.id = :id")
    Optional<GameSession> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(SessionStatus status);

    long countByStatusIn(List<SessionStatus> statuses);
}
