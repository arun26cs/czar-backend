package com.czar.voiceai.dto;

import java.util.UUID;

/**
 * A single tag entry passed by the client in the parse request context.
 * Allows the AI to auto-assign a suggestedTagId from the user's existing tags.
 */
public record TagContext(
        UUID id,
        String name,
        String colorHex) {
}
