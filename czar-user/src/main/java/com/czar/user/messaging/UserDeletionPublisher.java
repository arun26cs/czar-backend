package com.czar.user.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes user deletion events to Pub/Sub so downstream services can clean up.
 *
 * <p>On user.deleted:
 * - czar-auth subscribes and revokes all refresh tokens for the userId
 * - czar-notes / czar-planner can subscribe and soft-delete user data
 *
 * PubSubTemplate is optional — gracefully skips when not available (tests / no emulator).
 */
@Component
public class UserDeletionPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserDeletionPublisher.class);
    private static final String TOPIC = "czar-user-events";

    private final Optional<PubSubTemplate> pubSubTemplate;
    private final ObjectMapper objectMapper;

    public UserDeletionPublisher(Optional<PubSubTemplate> pubSubTemplate, ObjectMapper objectMapper) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishUserDeleted(UUID userId) {
        if (pubSubTemplate.isEmpty()) {
            log.debug("PubSubTemplate not available — skipping user.deleted event for userId={}", userId);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of("userId", userId.toString()));

            PubSubMessageEnvelope envelope = PubSubMessageEnvelope.builder()
                    .eventType("user.deleted")
                    .userId(userId.toString())
                    .payload(payload)
                    .build();

            String json = objectMapper.writeValueAsString(envelope);
            pubSubTemplate.get().publish(TOPIC, json);
            log.info("Published user.deleted event for userId={}", userId);
        } catch (Exception e) {
            log.error("Failed to publish user.deleted for userId={}: {}", userId, e.getMessage());
        }
    }
}
