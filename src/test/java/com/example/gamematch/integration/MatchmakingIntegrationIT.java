package com.example.gamematch.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.example.gamematch.match.model.Match;
import com.example.gamematch.match.model.MatchStatus;
import com.example.gamematch.match.repository.MatchRepository;
import com.example.gamematch.matchmaking.service.MatchmakingEngine;
import com.example.gamematch.matchmaking.service.MatchmakingQueueService;
import com.example.gamematch.player.model.GameMode;
import com.example.gamematch.player.model.Player;
import com.example.gamematch.player.model.Region;
import com.example.gamematch.player.repository.PlayerRepository;
import com.example.gamematch.session.model.GameSession;
import com.example.gamematch.session.model.MatchHistory;
import com.example.gamematch.session.model.SessionPlayer;
import com.example.gamematch.session.model.SessionStatus;
import com.example.gamematch.session.repository.GameSessionRepository;
import com.example.gamematch.session.repository.MatchHistoryRepository;
import com.example.gamematch.session.repository.SessionPlayerRepository;
import com.example.gamematch.session.service.GameSessionService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MatchmakingIntegrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private GameSessionRepository sessionRepository;

    @Autowired
    private SessionPlayerRepository sessionPlayerRepository;

    @Autowired
    private MatchHistoryRepository matchHistoryRepository;

    @Autowired
    private MatchmakingQueueService queueService;

    @Autowired
    private MatchmakingEngine matchmakingEngine;

    @Autowired
    private GameSessionService sessionService;

    @Test
    @DisplayName("Player profile creation and persistence in PostgreSQL")
    void testPlayerPersistence() throws Exception {
        String payload = """
                {
                    "displayName": "CyberWarrior",
                    "region": "NA",
                    "skillRating": 1400,
                    "preferredMode": "RANKED_1V1"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("CyberWarrior"))
                .andReturn();

        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/players/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("CyberWarrior"))
                .andExpect(jsonPath("$.skillRating").value(1400));

        Player player = playerRepository.findById(id.longValue()).orElseThrow();
        assertThat(player.getDisplayName()).isEqualTo("CyberWarrior");
    }

    @Test
    @DisplayName("Redis queue operations, duplicate prevention, and queue leaving")
    void testQueueOperationsAndDuplicatePrevention() throws Exception {
        // Create player
        String payload = """
                {
                    "displayName": "QueueTester",
                    "region": "EU",
                    "skillRating": 1100,
                    "preferredMode": "RANKED_1V1"
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        Number idNum = JsonPath.read(res.getResponse().getContentAsString(), "$.id");
        Long playerId = idNum.longValue();

        // 1. Join queue -> 201 Created
        mockMvc.perform(post("/api/v1/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": " + playerId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inQueue").value(true));

        assertThat(queueService.isPlayerQueued(playerId)).isTrue();

        // 2. Duplicate join -> 200 OK (idempotent duplicate prevention)
        mockMvc.perform(post("/api/v1/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": " + playerId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inQueue").value(true));

        // 3. Leave queue -> 200 OK
        mockMvc.perform(post("/api/v1/queue/leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": " + playerId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(true))
                .andExpect(jsonPath("$.inQueue").value(false));

        assertThat(queueService.isPlayerQueued(playerId)).isFalse();
    }

    @Test
    @DisplayName("Concurrent queue joins are processed safely without duplicate queue entries")
    void testConcurrentQueueJoins() throws Exception {
        // Create a single player
        String payload = """
                {
                    "displayName": "ConcurrencyTester",
                    "region": "AP",
                    "skillRating": 1500,
                    "preferredMode": "RANKED_1V1"
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        Number idNum = JsonPath.read(res.getResponse().getContentAsString(), "$.id");
        Long playerId = idNum.longValue();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                MvcResult mvcRes = mockMvc.perform(post("/api/v1/queue/join")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"playerId\": " + playerId + "}"))
                        .andReturn();
                return mvcRes.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        int createdCount = 0;
        int okCount = 0;
        for (Future<Integer> f : futures) {
            int statusCode = f.get();
            if (statusCode == 201) createdCount++;
            else if (statusCode == 200) okCount++;
        }

        // Exactly one request joined (201), the other 7 were recognized as already queued (200)
        assertThat(createdCount).isEqualTo(1);
        assertThat(okCount).isEqualTo(threadCount - 1);

        // Clean up
        queueService.leaveQueue(playerId);
    }

    @Test
    @DisplayName("End-to-end matchmaking, session lifecycle, ready-up, heartbeat, and completion")
    void testEndToEndMatchAndSessionFlow() throws Exception {
        // Create 2 compatible players in region OCE
        Long p1Id = createPlayerHelper("OCE_Player1", Region.OCE, 1200, GameMode.RANKED_1V1);
        Long p2Id = createPlayerHelper("OCE_Player2", Region.OCE, 1220, GameMode.RANKED_1V1);

        // Both join queue
        mockMvc.perform(post("/api/v1/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": " + p1Id + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/queue/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": " + p2Id + "}"))
                .andExpect(status().isCreated());

        // Verify players are matched and popped from queue
        assertThat(queueService.isPlayerQueued(p1Id)).isFalse();
        assertThat(queueService.isPlayerQueued(p2Id)).isFalse();

        // Check created match in PostgreSQL
        List<Match> matchList = matchRepository.findAll();
        Match match = matchList.stream()
                .filter(m -> m.getRegion() == Region.OCE)
                .findFirst().orElseThrow();
        assertThat(match.getStatus()).isIn(MatchStatus.WAITING, MatchStatus.READY);

        // Check created game session in PostgreSQL
        GameSession session = sessionRepository.findByMatchId(match.getId()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.WAITING_FOR_PLAYERS);

        // Player 1 readies up
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/ready")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": " + p1Id + "}"))
                .andExpect(status().isOk());

        // Player 2 readies up -> triggers ACTIVE state!
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/ready")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": " + p2Id + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Player 1 sends heartbeat
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\": " + p1Id + "}"))
                .andExpect(status().isOk());

        // Complete the session with Player 1 winning
        mockMvc.perform(post("/api/v1/sessions/" + session.getId() + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winnerPlayerId\": " + p1Id + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify player 1 rating increased (+25: 1200 -> 1225)
        Player p1Updated = playerRepository.findById(p1Id).orElseThrow();
        assertThat(p1Updated.getSkillRating()).isEqualTo(1225);

        // Verify player 2 rating decreased (-25: 1220 -> 1195)
        Player p2Updated = playerRepository.findById(p2Id).orElseThrow();
        assertThat(p2Updated.getSkillRating()).isEqualTo(1195);

        // Verify match history
        List<MatchHistory> history = matchHistoryRepository.findByPlayerIdOrderByRecordedAtDesc(p1Id);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getOutcome()).isEqualTo("WON");
        assertThat(history.get(0).getRatingChange()).isEqualTo(25);
    }

    private Long createPlayerHelper(String name, Region region, int rating, GameMode mode) throws Exception {
        String payload = String.format("""
                {
                    "displayName": "%s",
                    "region": "%s",
                    "skillRating": %d,
                    "preferredMode": "%s"
                }
                """, name, region.name(), rating, mode.name());

        MvcResult res = mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        Number idNum = JsonPath.read(res.getResponse().getContentAsString(), "$.id");
        return idNum.longValue();
    }
}
