package com.example.gamematch.player;

import java.time.Instant;

import com.example.gamematch.common.ApiExceptionHandler;
import com.example.gamematch.common.exception.ConflictException;
import com.example.gamematch.common.exception.ResourceNotFoundException;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.player.controller.PlayerController;
import com.example.gamematch.player.dto.PlayerResponse;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.service.PlayerService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(PlayerController.class)
@Import({ApiExceptionHandler.class})
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlayerService playerService;

    @MockBean
    private MatchmakingMetrics metrics;

    @Test
    @DisplayName("POST /api/v1/players returns 201 Created with valid payload")
    void createPlayerSuccess() throws Exception {
        PlayerResponse response = new PlayerResponse(
                1L, "GamerOne", Region.NA, 1000, GameMode.RANKED_1V1, Instant.now(), Instant.now()
        );
        when(playerService.createPlayer(any())).thenReturn(response);

        String json = """
                {
                    "displayName": "GamerOne",
                    "region": "NA",
                    "skillRating": 1000,
                    "preferredMode": "RANKED_1V1"
                }
                """;

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.displayName").value("GamerOne"))
                .andExpect(jsonPath("$.region").value("NA"));
    }

    @Test
    @DisplayName("POST /api/v1/players returns 400 Bad Request on invalid input")
    void createPlayerValidationError() throws Exception {
        String invalidJson = """
                {
                    "displayName": "",
                    "region": null,
                    "skillRating": -100
                }
                """;

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/v1/players returns 409 Conflict when username already exists")
    void createPlayerConflict() throws Exception {
        when(playerService.createPlayer(any())).thenThrow(new ConflictException("Player already exists"));

        String json = """
                {
                    "displayName": "DuplicateName",
                    "region": "NA",
                    "preferredMode": "RANKED_1V1"
                }
                """;

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    @DisplayName("GET /api/v1/players/{id} returns 200 OK")
    void getPlayerSuccess() throws Exception {
        PlayerResponse response = new PlayerResponse(
                1L, "GamerOne", Region.NA, 1000, GameMode.RANKED_1V1, Instant.now(), Instant.now()
        );
        when(playerService.getPlayer(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/players/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.displayName").value("GamerOne"));
    }

    @Test
    @DisplayName("GET /api/v1/players/{id} returns 404 when not found")
    void getPlayerNotFound() throws Exception {
        when(playerService.getPlayer(999L)).thenThrow(new ResourceNotFoundException("Player not found with id: 999"));

        mockMvc.perform(get("/api/v1/players/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }
}
