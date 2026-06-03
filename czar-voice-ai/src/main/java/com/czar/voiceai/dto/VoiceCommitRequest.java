package com.czar.voiceai.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for POST /api/v1/voice/commit.
 *
 * <p>All items must have their required fields filled (missingFields empty).
 * Server re-validates field presence independently of missingFields.
 */
public record VoiceCommitRequest(
        @NotEmpty List<ParsedItem> items) {
}
