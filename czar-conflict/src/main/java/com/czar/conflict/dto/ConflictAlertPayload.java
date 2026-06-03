package com.czar.conflict.dto;

import java.util.List;

/**
 * Payload serialised into the PubSubMessageEnvelope when conflicts are found.
 * Published to the czar-notifications topic.
 */
public record ConflictAlertPayload(
        List<String> planIds,
        String message) {
}
