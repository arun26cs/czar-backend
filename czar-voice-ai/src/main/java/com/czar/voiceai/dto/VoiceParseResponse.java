package com.czar.voiceai.dto;

import java.util.List;

/**
 * Response from POST /api/v1/voice/parse.
 *
 * <p>When {@code requiresInput} is true, at least one item has a non-empty
 * {@code missingFields} list. The client should display those items for the
 * user to fill in before calling /commit.
 */
public record VoiceParseResponse(
        List<ParsedItem> items,
        int parsedCount,
        boolean requiresInput) {
}
