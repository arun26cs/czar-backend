package com.czar.voiceai.service;

import com.czar.voiceai.dto.VoiceContext;
import com.czar.voiceai.dto.VoiceParseRequest;
import com.czar.voiceai.dto.VoiceParseResponse;
import com.czar.voiceai.exception.GroqRateLimitException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceParseServiceTest {

    @Mock
    GroqClient groqClient;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    VoiceParseService voiceParseService;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate TODAY = LocalDate.of(2025, 6, 2);
    private static final VoiceContext CTX = new VoiceContext(TODAY, "UTC");

    // -------------------------------------------------------------------------
    // Complete plan — no missing fields
    // -------------------------------------------------------------------------

    @Test
    void parse_completePlan_noMissingFields() {
        when(groqClient.chat(anyString(), anyString())).thenReturn(Mono.just(
                """
                [{"type":"plan","title":"Morning Run","scheduledDate":"2025-06-02",
                  "hour":7,"minute":0,"durationMinutes":30,"planType":"task"}]
                """));

        VoiceParseResponse resp = voiceParseService.parse(USER_ID,
                new VoiceParseRequest("Morning run at 7am for 30 mins", CTX)).block();

        assertThat(resp).isNotNull();
        assertThat(resp.parsedCount()).isEqualTo(1);
        assertThat(resp.requiresInput()).isFalse();
        assertThat(resp.items().get(0).type()).isEqualTo("plan");
        assertThat(resp.items().get(0).title()).isEqualTo("Morning Run");
        assertThat(resp.items().get(0).hour()).isEqualTo(7);
        assertThat(resp.items().get(0).durationMinutes()).isEqualTo(30);
        assertThat(resp.items().get(0).missingFields()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Incomplete plan — missing scheduledDate and hour
    // -------------------------------------------------------------------------

    @Test
    void parse_incompletePlan_flagsMissingFields() {
        when(groqClient.chat(anyString(), anyString())).thenReturn(Mono.just(
                """
                [{"type":"plan","title":"Gym","durationMinutes":60,"planType":"task"}]
                """));

        VoiceParseResponse resp = voiceParseService.parse(USER_ID,
                new VoiceParseRequest("Go to gym for an hour", null)).block();

        assertThat(resp.requiresInput()).isTrue();
        assertThat(resp.items().get(0).missingFields()).containsExactlyInAnyOrder("scheduledDate", "hour");
    }

    // -------------------------------------------------------------------------
    // Note — only title + body needed
    // -------------------------------------------------------------------------

    @Test
    void parse_note_noMissingFields() {
        when(groqClient.chat(anyString(), anyString())).thenReturn(Mono.just(
                """
                [{"type":"note","title":"Shopping list","body":"milk, bread, eggs"}]
                """));

        VoiceParseResponse resp = voiceParseService.parse(USER_ID,
                new VoiceParseRequest("Note: buy milk, bread and eggs", CTX)).block();

        assertThat(resp.items().get(0).type()).isEqualTo("note");
        assertThat(resp.items().get(0).title()).isEqualTo("Shopping list");
        assertThat(resp.items().get(0).body()).isEqualTo("milk, bread, eggs");
        assertThat(resp.items().get(0).missingFields()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Mixed: plan + note in one transcript
    // -------------------------------------------------------------------------

    @Test
    void parse_multipleItems_planAndNote() {
        when(groqClient.chat(anyString(), anyString())).thenReturn(Mono.just(
                """
                [
                  {"type":"plan","title":"Morning Run","scheduledDate":"2025-06-02","hour":7,"minute":0,"durationMinutes":30,"planType":"task"},
                  {"type":"note","title":"Call John","body":"Discuss Q2 roadmap"}
                ]
                """));

        VoiceParseResponse resp = voiceParseService.parse(USER_ID,
                new VoiceParseRequest("Run at 7, then note to call John", CTX)).block();

        assertThat(resp.parsedCount()).isEqualTo(2);
        assertThat(resp.items().get(0).type()).isEqualTo("plan");
        assertThat(resp.items().get(1).type()).isEqualTo("note");
        assertThat(resp.requiresInput()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Groq wraps JSON in markdown code fence — should still parse
    // -------------------------------------------------------------------------

    @Test
    void parse_jsonWithSurroundingText_extractsArray() {
        when(groqClient.chat(anyString(), anyString())).thenReturn(Mono.just(
                "Here are the items:\n[{\"type\":\"note\",\"title\":\"Reminder\",\"body\":\"Buy milk\"}]"));

        VoiceParseResponse resp = voiceParseService.parse(USER_ID,
                new VoiceParseRequest("Remind me to buy milk", null)).block();

        assertThat(resp.parsedCount()).isEqualTo(1);
        assertThat(resp.items().get(0).title()).isEqualTo("Reminder");
    }

    // -------------------------------------------------------------------------
    // Invalid Groq response — returns empty (no exception propagated)
    // -------------------------------------------------------------------------

    @Test
    void parse_invalidGroqResponse_returnsEmpty() {
        when(groqClient.chat(anyString(), anyString())).thenReturn(Mono.just("Not valid JSON"));

        VoiceParseResponse resp = voiceParseService.parse(USER_ID,
                new VoiceParseRequest("some transcript", null)).block();

        assertThat(resp.parsedCount()).isZero();
        assertThat(resp.items()).isEmpty();
        assertThat(resp.requiresInput()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Groq rate limit — exception propagates
    // -------------------------------------------------------------------------

    @Test
    void parse_groqRateLimit_propagatesException() {
        when(groqClient.chat(anyString(), anyString()))
                .thenReturn(Mono.error(new GroqRateLimitException("rate limited")));

        assertThatThrownBy(() ->
                voiceParseService.parse(USER_ID, new VoiceParseRequest("test", null)).block())
                .isInstanceOf(GroqRateLimitException.class);
    }

    // -------------------------------------------------------------------------
    // Generic Groq error — returns empty (graceful degradation)
    // -------------------------------------------------------------------------

    @Test
    void parse_genericGroqError_returnsEmpty() {
        when(groqClient.chat(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Groq service down")));

        VoiceParseResponse resp = voiceParseService.parse(USER_ID,
                new VoiceParseRequest("test", null)).block();

        assertThat(resp.parsedCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // Null context defaults to today + UTC
    // -------------------------------------------------------------------------

    @Test
    void parse_nullContext_usesDefaults() {
        when(groqClient.chat(anyString(), anyString())).thenReturn(Mono.just(
                "[{\"type\":\"note\",\"title\":\"Quick note\"}]"));

        VoiceParseResponse resp = voiceParseService.parse(USER_ID,
                new VoiceParseRequest("Quick note", null)).block();

        assertThat(resp.parsedCount()).isEqualTo(1);
    }
}
