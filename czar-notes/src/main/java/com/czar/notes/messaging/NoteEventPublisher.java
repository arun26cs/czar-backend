package com.czar.notes.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes note lifecycle events to czar-note-events Pub/Sub topic.
 * No-op when PubSubTemplate is unavailable (tests / no emulator).
 */
@Component
public class NoteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoteEventPublisher.class);
    private static final String TOPIC = "czar-note-events";

    private final Optional<PubSubTemplate> pubSubTemplate;
    private final ObjectMapper objectMapper;

    public NoteEventPublisher(Optional<PubSubTemplate> pubSubTemplate, ObjectMapper objectMapper) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String eventType, String userId, String noteId) {
        pubSubTemplate.ifPresentOrElse(
                template -> {
                    try {
                        PubSubMessageEnvelope envelope = PubSubMessageEnvelope.builder()
                                .eventType(eventType)
                                .userId(userId)
                                .eventId(UUID.randomUUID().toString())
                                .occurredAt(Instant.now())
                                .payload("{\"noteId\":\"" + noteId + "\"}")
                                .build();
                        template.publish(TOPIC, objectMapper.writeValueAsString(envelope));
                        log.debug("Published {} for noteId={}", eventType, noteId);
                    } catch (Exception e) {
                        log.error("Failed to publish {}: {}", eventType, e.getMessage());
                    }
                },
                () -> log.debug("PubSubTemplate not available — {} not published (dev mode)", eventType)
        );
    }
}
