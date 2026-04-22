package com.example.gamematch.session.repository;

import java.util.List;
import java.util.Optional;

import com.example.gamematch.session.model.SessionPlayer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionPlayerRepository extends JpaRepository<SessionPlayer, Long> {

    List<SessionPlayer> findBySessionId(Long sessionId);

    Optional<SessionPlayer> findBySessionIdAndPlayerId(Long sessionId, Long playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SessionPlayer sp WHERE sp.sessionId = :sessionId AND sp.playerId = :playerId")
    Optional<SessionPlayer> findBySessionIdAndPlayerIdForUpdate(@Param("sessionId") Long sessionId, @Param("playerId") Long playerId);
}
