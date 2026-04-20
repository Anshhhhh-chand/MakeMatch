package com.example.gamematch.match;

import java.time.Instant;
import java.util.List;

import com.example.gamematch.common.ApiExceptionHandler;
import com.example.gamematch.common.exception.ResourceNotFoundException;
import com.example.gamematch.match.controller.MatchController;
import com.example.gamematch.match.dto.MatchResponse;
import com.example.gamematch.match.model.MatchStatus;
import com.example.gamematch.match.service.MatchService;
import com.example.gamematch.observability.MatchmakingMetrics;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchController.class)
@Import({ApiExceptionHandler.class})
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchService matchService;

    @MockBean
    private MatchmakingMetrics metrics;

    @Test
    @DisplayName("GET /api/v1/matches/{id} returns 200 OK")
    void getMatchSuccess() throws Exception {
        MatchResponse.MatchPlayerDto p1 = new MatchResponse.MatchPlayerDto(1L, "PlayerOne", 1200, 1);
        MatchResponse.MatchPlayerDto p2 = new MatchResponse.MatchPlayerDto(2L, "PlayerTwo", 1250, 2);

        MatchResponse response = new MatchResponse(
                100L, Region.NA, GameMode.RANKED_1V1, MatchStatus.ACTIVE,
                1225, Instant.now(), null, List.of(p1, p2)
        );

        when(matchService.getMatchResponse(100L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/matches/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.region").value("NA"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.players.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/matches/{id} returns 404 when match not found")
    void getMatchNotFound() throws Exception {
        when(matchService.getMatchResponse(999L)).thenThrow(new ResourceNotFoundException("Match not found"));

        mockMvc.perform(get("/api/v1/matches/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }
}
