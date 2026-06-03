package com.czar.voiceai.dto;

import java.time.LocalDate;

/**
 * Context passed by the client to anchor relative date references in the transcript.
 */
public record VoiceContext(
        LocalDate date,
        String timezone) {
}
