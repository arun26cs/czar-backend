package com.czar.voiceai.dto;

import java.util.UUID;

/**
 * Response from POST /api/v1/voice/commit.
 *
 * <p>{@code jobId} can be used by Phase 8 consumers to correlate the
 * ai.result.ready event with the original voice request.
 */
public record VoiceCommitResponse(
        UUID jobId,
        int published) {
}
