package com.czar.common.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard Pub/Sub message envelope shared by ALL inter-service events.
 *
 * <p>Every service MUST wrap its event payload in this envelope before
 * publishing to any Pub/Sub topic. Consumers MUST validate {@code eventType}
 * before processing.
 *
 * <p>Example event types:
 * <ul>
 *   <li>{@code user.created}     — czar-auth → czar-user-events topic
 *   <li>{@code plan.created}     — czar-planner → czar-plan-events topic
 *   <li>{@code plan.updated}     — czar-planner → czar-plan-events topic
 *   <li>{@code note.created}     — czar-notes → czar-note-events topic
 *   <li>{@code ai.result.ready}  — czar-voice-ai → czar-ai-results topic
 *   <li>{@code notification.push}— czar-conflict/planner → czar-notifications topic
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PubSubMessageEnvelope {

    /** Dot-separated event type, e.g. {@code user.created}, {@code plan.updated}. */
    private String eventType;

    /** UUID of the user this event belongs to. */
    private String userId;

    /** Unique event ID for idempotency / deduplication on the consumer side. */
    private String eventId;

    /** UTC timestamp when the event was produced. */
    private Instant occurredAt;

    /** Event-specific payload serialised as a JSON string. */
    private String payload;
}
