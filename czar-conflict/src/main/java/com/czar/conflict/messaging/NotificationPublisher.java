package com.czar.conflict.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.czar.conflict.dto.ConflictAlertPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes conflict-alert notifications to the czar-notifications Pub/Sub topic.
 * No-op when PubSubTemplate is unavailable (tests / no emulator).
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private static final String TOPIC = "czar-notifications";

    private final Optional<PubSubTemplate> pubSubTemplate;
    private final ObjectMapper objectMapper;

    public NotificationPublisher(Optional<PubSubTemplate> pubSubTemplate, ObjectMapper objectMapper) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishConflictAlert(String userId, ConflictAlertPayload payload) {
        pubSubTemplate.ifPresentOrElse(
                template -> {
                    try {
                        PubSubMessageEnvelope envelope = PubSubMessageEnvelope.builder()
                                .eventType("conflict_alert")
                                .userId(userId)
                                .eventId(UUID.randomUUID().toString())
                                .occurredAt(Instant.now())
                                .payload(objectMapper.writeValueAsString(payload))
                                .build();
                        template.publish(TOPIC, objectMapper.writeValueAsString(envelope));
                        log.info("Published conflict_alert for userId={} planIds={}",
                                userId, payload.planIds());
                    } catch (Exception e) {
                        log.error("Failed to publish conflict alert for userId={}: {}",
                                userId, e.getMessage());
                    }
                },
                () -> log.debug("PubSubTemplate not available — conflict alert not published (dev mode)")
        );
    }
}
