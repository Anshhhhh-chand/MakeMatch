package com.example.gamematch.matchmaking.controller;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.example.gamematch.matchmaking.dto.JoinQueueRequest;
import com.example.gamematch.matchmaking.dto.LeaveQueueRequest;
import com.example.gamematch.matchmaking.dto.QueueStatusResponse;
import com.example.gamematch.matchmaking.model.QueueEntry;
import com.example.gamematch.matchmaking.service.MatchmakingEngine;
import com.example.gamematch.matchmaking.service.MatchmakingQueueService;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.service.PlayerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
public class MatchmakingController {

    private final MatchmakingQueueService queueService;
    private final MatchmakingEngine matchmakingEngine;
    private final PlayerService playerService;
    private final Clock clock;

    public MatchmakingController(MatchmakingQueueService queueService,
                                 MatchmakingEngine matchmakingEngine,
                                 PlayerService playerService,
                                 Clock clock) {
        this.queueService = queueService;
        this.matchmakingEngine = matchmakingEngine;
        this.playerService = playerService;
        this.clock = clock;
    }

    @PostMapping("/join")
    public ResponseEntity<QueueStatusResponse> joinQueue(@Valid @RequestBody JoinQueueRequest request) {
        Player player = playerService.getPlayerEntity(request.playerId());

        Region region = request.region() != null ? request.region() : player.getRegion();
        GameMode mode = request.mode() != null ? request.mode() : player.getPreferredMode();
        Instant now = clock.instant();

        Long playerId = request.playerId();
        QueueEntry entry = new QueueEntry(playerId, region, mode, player.getSkillRating(), now);
        boolean joined = queueService.joinQueue(entry);

        // Opportunistically trigger evaluation
        if (joined) {
            matchmakingEngine.evaluateQueue(region, mode);
        }

        QueueStatusResponse status = getQueueStatus(playerId);
        if (joined) {
            return ResponseEntity.created(URI.create("/api/v1/queue/status/" + playerId))
                    .body(status);
        } else {
            // Idempotent: player is already in queue
            return ResponseEntity.ok(status);
        }
    }

    @PostMapping("/leave")
    public ResponseEntity<Map<String, Object>> leaveQueue(@Valid @RequestBody LeaveQueueRequest request) {
        boolean removed = queueService.leaveQueue(request.playerId());
        Map<String, Object> response = new HashMap<>();
        response.put("playerId", request.playerId());
        response.put("removed", removed);
        response.put("inQueue", false);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{playerId}")
    public ResponseEntity<QueueStatusResponse> getStatus(@PathVariable Long playerId) {
        return ResponseEntity.ok(getQueueStatus(playerId));
    }

    @GetMapping("/size")
    public ResponseEntity<Map<String, Object>> getQueueSizes(
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) GameMode mode) {
        Map<String, Object> response = new HashMap<>();
        response.put("totalActiveInQueue", queueService.getTotalQueueSize());

        if (region != null && mode != null) {
            response.put("queueSize", queueService.getQueueSize(region, mode));
            response.put("region", region);
            response.put("mode", mode);
        } else {
            Map<String, Long> perQueue = new HashMap<>();
            for (Region r : Region.values()) {
                for (GameMode m : GameMode.values()) {
                    long size = queueService.getQueueSize(r, m);
                    if (size > 0) {
                        perQueue.put(r.name() + ":" + m.name(), size);
                    }
                }
            }
            response.put("queues", perQueue);
        }

        return ResponseEntity.ok(response);
    }

    private QueueStatusResponse getQueueStatus(Long playerId) {
        Optional<QueueEntry> entryOpt = queueService.getQueueEntry(playerId);
        boolean inQueue = entryOpt.isPresent() && queueService.isPlayerQueued(playerId);

        if (inQueue && entryOpt.isPresent()) {
            QueueEntry entry = entryOpt.get();
            long modeSize = queueService.getQueueSize(entry.region(), entry.mode());
            long totalSize = queueService.getTotalQueueSize();
            return new QueueStatusResponse(
                    playerId,
                    true,
                    entry.region(),
                    entry.mode(),
                    entry.skillRating(),
                    modeSize,
                    totalSize,
                    entry.joinedAt()
            );
        } else {
            Player player = playerService.getPlayerEntity(playerId);
            return new QueueStatusResponse(
                    playerId,
                    false,
                    player.getRegion(),
                    player.getPreferredMode(),
                    player.getSkillRating(),
                    0L,
                    queueService.getTotalQueueSize(),
                    null
            );
        }
    }
}
