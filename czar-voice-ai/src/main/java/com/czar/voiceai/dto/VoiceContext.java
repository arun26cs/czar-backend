package com.czar.voiceai.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Context passed by the client to anchor relative date references in the transcript
 * and to allow the AI to suggest tags from the user's existing tag set.
 */
public record VoiceContext(
        LocalDate date,
        String timezone,
        List<TagContext> existingTags) {
}
