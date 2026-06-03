package com.czar.planner.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes plan lifecycle events to czar-plan-events Pub/Sub topic.
 * No-op when PubSubTemplate is unavailable (tests / no emulator).
 */
@Component
public class PlanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PlanEventPublisher.class);
    private static final String TOPIC = "czar-plan-events";

    private final Optional<PubSubTemplate> pubSubTemplate;
    private final ObjectMapper objectMapper;

    public PlanEventPublisher(Optional<PubSubTemplate> pubSubTemplate, ObjectMapper objectMapper) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    /** Publish without scheduledDate (backward-compat). */
    public void publish(String eventType, String userId, String planId) {
        publish(eventType, userId, planId, null);
    }

    /** Publish with scheduledDate so czar-conflict can query plans without an extra round-trip. */
    public void publish(String eventType, String userId, String planId, LocalDate scheduledDate) {
        pubSubTemplate.ifPresentOrElse(
                template -> {
                    try {
                        Map<String, String> payloadMap = new LinkedHashMap<>();
                        payloadMap.put("planId", planId);
                        if (scheduledDate != null) {
                            payloadMap.put("scheduledDate", scheduledDate.toString());
                        }
                        PubSubMessageEnvelope envelope = PubSubMessageEnvelope.builder()
                                .eventType(eventType)
                                .userId(userId)
                                .eventId(UUID.randomUUID().toString())
                                .occurredAt(Instant.now())
                                .payload(objectMapper.writeValueAsString(payloadMap))
                                .build();
                        template.publish(TOPIC, objectMapper.writeValueAsString(envelope));
                        log.debug("Published {} for planId={} scheduledDate={}", eventType, planId, scheduledDate);
                    } catch (Exception e) {
                        log.error("Failed to publish {}: {}", eventType, e.getMessage());
                    }
                },
                () -> log.debug("PubSubTemplate not available — {} not published (dev mode)", eventType)
        );
    }
}
