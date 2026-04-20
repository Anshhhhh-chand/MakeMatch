package com.example.gamematch.matchmaking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.example.gamematch.common.ApiExceptionHandler;
import com.example.gamematch.matchmaking.controller.MatchmakingController;
import com.example.gamematch.matchmaking.model.QueueEntry;
import com.example.gamematch.matchmaking.service.MatchmakingEngine;
import com.example.gamematch.matchmaking.service.MatchmakingQueueService;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchmakingController.class)
@Import({ApiExceptionHandler.class})
class MatchmakingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchmakingQueueService queueService;

    @MockBean
    private MatchmakingEngine matchmakingEngine;

    @MockBean
    private PlayerService playerService;

    @MockBean
    private MatchmakingMetrics metrics;

    @MockBean
    private Clock clock;

    private final Instant now = Instant.parse("2026-10-23T12:00:00Z");

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
    }

    @Test
    @DisplayName("POST /api/v1/queue/join returns 201 Created when joining queue")
    void joinQueueSuccess() throws Exception {
        Player player = new Player(1L, "PlayerOne", Region.NA, 1200, GameMode.RANKED_1V1, now);
        when(playerService.getPlayerEntity(1L)).thenReturn(player);
        when(queueService.joinQueue(any())).thenReturn(true);
        when(queueService.getQueueEntry(1L)).thenReturn(Optional.of(new QueueEntry(1L, Region.NA, GameMode.RANKED_1V1, 1200, now)));
        when(queueService.isPlayerQueued(1L)).thenReturn(true);
        when(queueService.getQueueSize(Region.NA, GameMode.RANKED_1V1)).thenReturn(1L);
        when(queueService.getTotalQueueSize()).thenReturn(1L);

        mockMvc.perform(post("/api/v1/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": 1, \"region\": \"NA\", \"mode\": \"RANKED_1V1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerId").value(1))
                .andExpect(jsonPath("$.inQueue").value(true))
                .andExpect(jsonPath("$.region").value("NA"));

        verify(matchmakingEngine).evaluateQueue(Region.NA, GameMode.RANKED_1V1);
    }

    @Test
    @DisplayName("POST /api/v1/queue/join is idempotent and returns 200 OK if already queued")
    void joinQueueIdempotent() throws Exception {
        Player player = new Player(1L, "PlayerOne", Region.NA, 1200, GameMode.RANKED_1V1, now);
        when(playerService.getPlayerEntity(1L)).thenReturn(player);
        when(queueService.joinQueue(any())).thenReturn(false); // already in queue
        when(queueService.getQueueEntry(1L)).thenReturn(Optional.of(new QueueEntry(1L, Region.NA, GameMode.RANKED_1V1, 1200, now)));
        when(queueService.isPlayerQueued(1L)).thenReturn(true);
        when(queueService.getQueueSize(Region.NA, GameMode.RANKED_1V1)).thenReturn(1L);
        when(queueService.getTotalQueueSize()).thenReturn(1L);

        mockMvc.perform(post("/api/v1/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inQueue").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/queue/leave removes player from queue")
    void leaveQueue() throws Exception {
        when(queueService.leaveQueue(1L)).thenReturn(true);

        mockMvc.perform(post("/api/v1/queue/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(true))
                .andExpect(jsonPath("$.inQueue").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/queue/size returns queue sizes")
    void getQueueSizes() throws Exception {
        when(queueService.getTotalQueueSize()).thenReturn(10L);
        when(queueService.getQueueSize(Region.NA, GameMode.RANKED_1V1)).thenReturn(4L);

        mockMvc.perform(get("/api/v1/queue/size?region=NA&mode=RANKED_1V1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveInQueue").value(10))
                .andExpect(jsonPath("$.queueSize").value(4));
    }
}
