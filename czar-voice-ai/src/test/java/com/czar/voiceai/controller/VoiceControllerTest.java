package com.czar.voiceai.controller;

import com.czar.voiceai.dto.*;
import com.czar.voiceai.exception.GroqRateLimitException;
import com.czar.voiceai.service.VoiceCommitService;
import com.czar.voiceai.service.VoiceParseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@WebFluxTest(VoiceController.class)
@Import(com.czar.voiceai.config.SecurityConfig.class)
class VoiceControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    VoiceParseService voiceParseService;

    @MockBean
    VoiceCommitService voiceCommitService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final UUID USER_UUID = UUID.fromString(USER_ID);
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 2);

    // -------------------------------------------------------------------------
    // POST /api/v1/voice/parse — 200 OK
    // -------------------------------------------------------------------------

    @Test
    void parse_returns200WithItems() throws Exception {
        ParsedItem item = new ParsedItem("plan", "Morning Run",
                TODAY, 7, 0, 30, null, "task", null, null, List.of());
        VoiceParseResponse response = new VoiceParseResponse(List.of(item), 1, false);

        when(voiceParseService.parse(any(), any())).thenReturn(Mono.just(response));

        String body = objectMapper.writeValueAsString(
                new VoiceParseRequest("Morning run at 7am for 30 mins",
                        new VoiceContext(TODAY, "UTC", null)));

        webTestClient.mutateWith(mockUser(USER_ID))
                .post().uri("/api/v1/voice/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.parsedCount").isEqualTo(1)
                .jsonPath("$.requiresInput").isEqualTo(false)
                .jsonPath("$.items[0].type").isEqualTo("plan")
                .jsonPath("$.items[0].title").isEqualTo("Morning Run");
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/voice/parse — items with missing fields (requiresInput = true)
    // -------------------------------------------------------------------------

    @Test
    void parse_returns200WithRequiresInput() throws Exception {
        ParsedItem item = new ParsedItem("plan", "Gym",
                null, null, null, 60, null, "task", null, null, List.of("scheduledDate", "hour"));
        VoiceParseResponse response = new VoiceParseResponse(List.of(item), 1, true);

        when(voiceParseService.parse(any(), any())).thenReturn(Mono.just(response));

        String body = objectMapper.writeValueAsString(
                new VoiceParseRequest("Gym for an hour", null));

        webTestClient.mutateWith(mockUser(USER_ID))
                .post().uri("/api/v1/voice/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requiresInput").isEqualTo(true)
                .jsonPath("$.items[0].missingFields[0]").exists();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/voice/parse — 400 when transcript is blank
    // -------------------------------------------------------------------------

    @Test
    void parse_returns400_whenTranscriptBlank() throws Exception {
        String body = objectMapper.writeValueAsString(
                new VoiceParseRequest("  ", null));

        webTestClient.mutateWith(mockUser(USER_ID))
                .post().uri("/api/v1/voice/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/voice/parse — 503 on Groq rate limit
    // -------------------------------------------------------------------------

    @Test
    void parse_returns503_onGroqRateLimit() throws Exception {
        when(voiceParseService.parse(any(), any()))
                .thenReturn(Mono.error(new GroqRateLimitException("rate limited")));

        String body = objectMapper.writeValueAsString(
                new VoiceParseRequest("Morning run", null));

        webTestClient.mutateWith(mockUser(USER_ID))
                .post().uri("/api/v1/voice/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().exists("Retry-After");
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/voice/commit — 200 OK with jobId
    // -------------------------------------------------------------------------

    @Test
    void commit_returns200WithJobId() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(voiceCommitService.commit(any(), any()))
                .thenReturn(new VoiceCommitResponse(jobId, 2));

        ParsedItem plan = new ParsedItem("plan", "Morning Run",
                TODAY, 7, 0, 30, null, "task", null, null, List.of());
        ParsedItem note = new ParsedItem("note", "Shopping list",
                null, null, null, null, "milk", null, null, null, List.of());

        String body = objectMapper.writeValueAsString(new VoiceCommitRequest(List.of(plan, note)));

        webTestClient.mutateWith(mockUser(USER_ID))
                .post().uri("/api/v1/voice/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobId").isEqualTo(jobId.toString())
                .jsonPath("$.published").isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/voice/commit — 400 when items list is empty
    // -------------------------------------------------------------------------

    @Test
    void commit_returns400_whenItemsEmpty() throws Exception {
        String body = objectMapper.writeValueAsString(new VoiceCommitRequest(Collections.emptyList()));

        webTestClient.mutateWith(mockUser(USER_ID))
                .post().uri("/api/v1/voice/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/voice/commit — 422 when service rejects invalid item
    // -------------------------------------------------------------------------

    @Test
    void commit_returns422_whenItemHasMissingFields() throws Exception {
        when(voiceCommitService.commit(any(), any()))
                .thenThrow(new IllegalArgumentException("Plan scheduledDate is required"));

        ParsedItem bad = new ParsedItem("plan", "Run",
                null, 7, 0, 30, null, "task", null, null, List.of());

        String body = objectMapper.writeValueAsString(new VoiceCommitRequest(List.of(bad)));

        webTestClient.mutateWith(mockUser(USER_ID))
                .post().uri("/api/v1/voice/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.detail").value(s -> ((String) s).contains("scheduledDate"));
    }
}
