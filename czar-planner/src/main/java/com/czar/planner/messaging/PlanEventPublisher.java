package com.czar.planner.messaging;

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

    public void publish(String eventType, String userId, String planId) {
        pubSubTemplate.ifPresentOrElse(
                template -> {
                    try {
                        PubSubMessageEnvelope envelope = PubSubMessageEnvelope.builder()
                                .eventType(eventType)
                                .userId(userId)
                                .eventId(UUID.randomUUID().toString())
                                .occurredAt(Instant.now())
                                .payload("{\"planId\":\"" + planId + "\"}")
                                .build();
                        template.publish(TOPIC, objectMapper.writeValueAsString(envelope));
                        log.debug("Published {} for planId={}", eventType, planId);
                    } catch (Exception e) {
                        log.error("Failed to publish {}: {}", eventType, e.getMessage());
                    }
                },
                () -> log.debug("PubSubTemplate not available — {} not published (dev mode)", eventType)
        );
    }
}
