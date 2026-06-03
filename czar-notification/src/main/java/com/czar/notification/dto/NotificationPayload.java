package com.czar.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Payload carried inside the PubSubMessageEnvelope for czar-notifications events.
 *
 * <p>Supported types:
 * <ul>
 *   <li>{@code conflict_alert}  — planIds contains the two conflicting plan UUIDs
 *   <li>{@code plan_reminder}   — planIds contains the plan to remind about
 *   <li>{@code missed_plan}     — planIds contains the missed plan
 *   <li>{@code ai_result_ready} — jobId carries the voice-parse job identifier
 *   <li>{@code note_saved}      — confirmation for voice-dictated notes
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationPayload(
        String type,
        List<String> planIds,
        String message,
        String jobId) {
}
