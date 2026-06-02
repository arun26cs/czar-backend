package com.czar.auth.service;

import com.czar.auth.entity.UserAuth;
import com.czar.common.messaging.PubSubMessageEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Publishes auth events to Pub/Sub topics.
 * On first user registration: publishes "user.created" to czar-user-events.
 * The local Pub/Sub emulator receives this; in prod, real GCP Pub/Sub is used.
 * PubSubTemplate is optional — gracefully skips publishing when not available (e.g. tests).
 */
@Service
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);
    private static final String TOPIC = "czar-user-events";

    private final Optional<PubSubTemplate> pubSubTemplate;
    private final ObjectMapper objectMapper;

    public UserEventPublisher(Optional<PubSubTemplate> pubSubTemplate, ObjectMapper objectMapper) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishUserCreated(UserAuth user) {
        if (pubSubTemplate.isEmpty()) {
            log.debug("PubSubTemplate not available — skipping user.created event for userId={}", user.getId());
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "userId", user.getId().toString(),
                    "email", user.getEmail() != null ? user.getEmail() : "",
                    "phone", user.getPhone() != null ? user.getPhone() : ""
            ));

            PubSubMessageEnvelope envelope = PubSubMessageEnvelope.builder()
                    .eventType("user.created")
                    .userId(user.getId().toString())
                    .payload(payload)
                    .build();

            String json = objectMapper.writeValueAsString(envelope);
            pubSubTemplate.get().publish(TOPIC, json);
            log.info("Published user.created event for userId={}", user.getId());
        } catch (Exception e) {
            // Non-fatal: log and continue — user is already persisted
            log.error("Failed to publish user.created for userId={}: {}", user.getId(), e.getMessage());
        }
    }
}
