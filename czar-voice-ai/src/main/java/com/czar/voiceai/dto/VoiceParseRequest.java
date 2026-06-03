package com.czar.voiceai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/voice/parse.
 */
public record VoiceParseRequest(
        @NotBlank String transcript,
        @Valid VoiceContext context) {
}
