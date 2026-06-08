package com.czar.voiceai.service;

import com.czar.voiceai.dto.ParsedItem;
import com.czar.voiceai.dto.TagContext;
import com.czar.voiceai.dto.VoiceContext;
import com.czar.voiceai.dto.VoiceParseRequest;
import com.czar.voiceai.dto.VoiceParseResponse;
import com.czar.voiceai.exception.GroqRateLimitException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parses a voice transcript into structured plan and note items via the Groq API.
 *
 * <p>Flow:
 * <ol>
 *   <li>Build a structured system prompt with today's date and timezone.
 *   <li>Call {@link GroqClient} with the transcript as the user message.
 *   <li>Extract the JSON array from the raw Groq content.
 *   <li>Map each raw JSON object to a {@link ParsedItem}, computing {@code missingFields}.
 *   <li>Set {@code requiresInput} if any item has missing required fields.
 * </ol>
 *
 * <p>On any non-rate-limit Groq error, returns an empty parse response instead of propagating.
 * Rate limit errors ({@link GroqRateLimitException}) are propagated for the controller to handle.
 */
@Service
public class VoiceParseService {

    private static final Logger log = LoggerFactory.getLogger(VoiceParseService.class);

    private static final String SYSTEM_PROMPT = """
            You are a voice assistant that parses speech into structured items.
            Return a JSON array of items. Each item must include:
            - "type": "plan" or "note"
            - "title": string (always required)
            For type "plan": also include scheduledDate (YYYY-MM-DD), hour (0-23 integer),
              minute (0-59 integer), durationMinutes (integer > 0), planType ("task"|"event"|"reminder").
              Use null for any field not mentioned.
            For type "note": also include body (string). Use null if not mentioned.
            Tag assignment: if the user's available tags are provided, pick the best matching tag by name.
              Include "suggestedTagName" (the tag name string) and "suggestedTagId" (its UUID string) in the item.
              Use null if no tag is a good match. Do NOT invent tag names not in the provided list.
            Today's date is %s. Timezone: %s.
            Available tags: %s
            Respond ONLY with a valid JSON array. No markdown, no explanation.
            Example: [{"type":"plan","title":"Run","scheduledDate":"2025-06-02","hour":7,"minute":0,"durationMinutes":30,"planType":"task","suggestedTagName":"Health","suggestedTagId":"uuid-here"}]
            """;

    private final GroqClient groqClient;
    private final ObjectMapper objectMapper;

    public VoiceParseService(GroqClient groqClient, ObjectMapper objectMapper) {
        this.groqClient = groqClient;
        this.objectMapper = objectMapper;
    }

    public Mono<VoiceParseResponse> parse(UUID userId, VoiceParseRequest request) {
        VoiceContext ctx = request.context();
        LocalDate date = (ctx != null && ctx.date() != null) ? ctx.date() : LocalDate.now();
        String timezone = (ctx != null && ctx.timezone() != null && !ctx.timezone().isBlank())
                ? ctx.timezone() : "UTC";

        // Build tags summary for the prompt
        String tagsJson = "none";
        if (ctx != null && ctx.existingTags() != null && !ctx.existingTags().isEmpty()) {
            tagsJson = ctx.existingTags().stream()
                    .map(t -> "{\"id\":\"" + t.id() + "\",\"name\":\"" + t.name() + "\"}")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }

        String systemPrompt = SYSTEM_PROMPT.formatted(date, timezone, tagsJson);

        return groqClient.chat(systemPrompt, request.transcript())
                .map(this::parseGroqContent)
                .onErrorResume(ex -> {
                    if (ex instanceof GroqRateLimitException) {
                        return Mono.error(ex);  // propagate — controller returns 503
                    }
                    log.warn("Groq parse failed for userId={}: {}", userId, ex.getMessage());
                    return Mono.just(new VoiceParseResponse(Collections.emptyList(), 0, false));
                });
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private VoiceParseResponse parseGroqContent(String content) {
        try {
            String json = extractJsonArray(content);
            List<Map<String, Object>> rawItems = objectMapper.readValue(
                    json, new TypeReference<>() {});

            List<ParsedItem> items = rawItems.stream()
                    .map(this::mapToItem)
                    .toList();

            boolean requiresInput = items.stream()
                    .anyMatch(i -> i.missingFields() != null && !i.missingFields().isEmpty());

            return new VoiceParseResponse(items, items.size(), requiresInput);
        } catch (Exception ex) {
            log.warn("Failed to parse Groq content: {}", ex.getMessage());
            return new VoiceParseResponse(Collections.emptyList(), 0, false);
        }
    }

    private ParsedItem mapToItem(Map<String, Object> raw) {
        String type = str(raw, "type", "note");
        String title = str(raw, "title", "");
        String suggestedTagName = str(raw, "suggestedTagName", null);
        UUID suggestedTagId = parseUuid(raw.get("suggestedTagId"));

        if ("plan".equals(type)) {
            LocalDate scheduledDate = parseDate(raw.get("scheduledDate"));
            Integer hour = num(raw, "hour");
            Integer minute = num(raw, "minute");
            Integer duration = num(raw, "durationMinutes");
            String planType = str(raw, "planType", "task");
            List<String> missing = missingPlanFields(title, scheduledDate, hour, duration);
            return new ParsedItem(type, title, scheduledDate, hour, minute, duration,
                    null, planType, suggestedTagId, suggestedTagName, missing);
        } else {
            // note
            String body = str(raw, "body", null);
            List<String> missing = (title == null || title.isBlank()) ? List.of("title") : List.of();
            return new ParsedItem("note", title, null, null, null, null,
                    body, null, suggestedTagId, suggestedTagName, missing);
        }
    }

    private List<String> missingPlanFields(String title, LocalDate date, Integer hour, Integer duration) {
        List<String> missing = new ArrayList<>();
        if (title == null || title.isBlank()) missing.add("title");
        if (date == null)                     missing.add("scheduledDate");
        if (hour == null)                     missing.add("hour");
        if (duration == null)                 missing.add("durationMinutes");
        return missing;
    }

    private String extractJsonArray(String content) {
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalArgumentException("No JSON array found in Groq response");
        }
        return content.substring(start, end + 1);
    }

    private String str(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private Integer num(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private LocalDate parseDate(Object val) {
        if (val == null) return null;
        try { return LocalDate.parse(val.toString()); }
        catch (Exception e) { return null; }
    }

    private UUID parseUuid(Object val) {
        if (val == null) return null;
        try { return UUID.fromString(val.toString()); }
        catch (Exception e) { return null; }
    }
}
