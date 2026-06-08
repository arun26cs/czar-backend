package com.czar.voiceai.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A single structured item extracted from a voice transcript.
 *
 * <p>Plans require: title, scheduledDate, hour (0-23), durationMinutes.
 * Notes require: title only; body is optional.
 *
 * <p>{@code missingFields} lists any required fields that Groq could not extract.
 * When non-empty, the client must prompt the user to fill them before calling /commit.
 */
public record ParsedItem(
        String type,              // "plan" | "note"
        String title,
        LocalDate scheduledDate,
        Integer hour,
        Integer minute,
        Integer durationMinutes,
        String body,
        String planType,          // "task" | "event" | "reminder" (plans only)
        UUID suggestedTagId,      // matched from existingTags by name (nullable)
        String suggestedTagName,  // tag name as suggested by AI (nullable)
        List<String> missingFields) {
}
