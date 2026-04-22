package com.example.gamematch.session.controller;

import java.util.List;

import com.example.gamematch.session.dto.CancelSessionRequest;
import com.example.gamematch.session.dto.CompleteSessionRequest;
import com.example.gamematch.session.dto.HeartbeatRequest;
import com.example.gamematch.session.dto.ReadyRequest;
import com.example.gamematch.session.dto.SessionResponse;
import com.example.gamematch.session.model.MatchHistory;
import com.example.gamematch.session.service.GameSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class GameSessionController {

    private final GameSessionService sessionService;

    public GameSessionController(GameSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable Long id) {
        SessionResponse response = sessionService.getSessionResponse(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/ready")
    public ResponseEntity<SessionResponse> readyPlayer(
            @PathVariable Long id,
            @Valid @RequestBody ReadyRequest request) {
        SessionResponse response = sessionService.playerReady(id, request.playerId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<SessionResponse> playerHeartbeat(
            @PathVariable Long id,
            @Valid @RequestBody HeartbeatRequest request) {
        SessionResponse response = sessionService.playerHeartbeat(id, request.playerId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<SessionResponse> completeSession(
            @PathVariable Long id,
            @Valid @RequestBody CompleteSessionRequest request) {
        SessionResponse response = sessionService.completeSession(id, request.winnerPlayerId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<SessionResponse> cancelSession(
            @PathVariable Long id,
            @RequestBody(required = false) CancelSessionRequest request) {
        String reason = request != null ? request.reason() : "Cancelled by user";
        SessionResponse response = sessionService.cancelSession(id, reason);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{playerId}")
    public ResponseEntity<List<MatchHistory>> getPlayerHistory(@PathVariable Long playerId) {
        List<MatchHistory> history = sessionService.getPlayerHistory(playerId);
        return ResponseEntity.ok(history);
    }
}
