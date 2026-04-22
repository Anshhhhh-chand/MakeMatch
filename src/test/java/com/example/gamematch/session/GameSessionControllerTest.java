package com.example.gamematch.session;

import java.time.Instant;
import java.util.List;

import com.example.gamematch.common.ApiExceptionHandler;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.session.controller.GameSessionController;
import com.example.gamematch.session.dto.SessionResponse;
import com.example.gamematch.session.model.SessionStatus;
import com.example.gamematch.session.service.GameSessionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameSessionController.class)
@Import({ApiExceptionHandler.class})
class GameSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameSessionService sessionService;

    @MockBean
    private MatchmakingMetrics metrics;

    @Test
    @DisplayName("GET /api/v1/sessions/{id} returns 200 OK")
    void getSession() throws Exception {
        SessionResponse.SessionPlayerDto p1 = new SessionResponse.SessionPlayerDto(1L, "P1", true, true, Instant.now());
        SessionResponse response = new SessionResponse(10L, 100L, SessionStatus.ACTIVE, "server:7777", Instant.now(), null, List.of(p1));

        when(sessionService.getSessionResponse(10L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/sessions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.serverAddress").value("server:7777"));
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/ready handles ready request")
    void readyPlayer() throws Exception {
        SessionResponse response = new SessionResponse(10L, 100L, SessionStatus.ACTIVE, "server:7777", Instant.now(), null, List.of());
        when(sessionService.playerReady(eq(10L), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/sessions/10/ready")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/heartbeat records heartbeat")
    void heartbeat() throws Exception {
        SessionResponse response = new SessionResponse(10L, 100L, SessionStatus.ACTIVE, "server:7777", Instant.now(), null, List.of());
        when(sessionService.playerHeartbeat(eq(10L), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/sessions/10/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/complete finishes session")
    void completeSession() throws Exception {
        SessionResponse response = new SessionResponse(10L, 100L, SessionStatus.COMPLETED, "server:7777", Instant.now(), Instant.now(), List.of());
        when(sessionService.completeSession(eq(10L), eq(1L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/sessions/10/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winnerPlayerId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
