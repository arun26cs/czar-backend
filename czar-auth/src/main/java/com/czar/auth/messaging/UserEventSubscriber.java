package com.czar.auth.messaging;

import com.czar.auth.repository.RefreshTokenRepository;
import com.czar.common.messaging.PubSubMessageEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Listens to czar-user-events Pub/Sub subscription for user lifecycle events.
 *
 * <p>On user.deleted: revokes all active refresh tokens for that user so their
 * sessions are immediately invalidated — they cannot obtain new access tokens
 * with an old refresh token after account deletion.
 *
 * PubSubTemplate is optional — no-op when emulator is unavailable (tests).
 */
@Component
public class UserEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(UserEventSubscriber.class);
    private static final String SUBSCRIPTION = "czar-user-events-auth-sub";

    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;

    public UserEventSubscriber(
            Optional<PubSubTemplate> pubSubTemplate,
            RefreshTokenRepository refreshTokenRepository,
            ObjectMapper objectMapper) {

        this.refreshTokenRepository = refreshTokenRepository;
        this.objectMapper = objectMapper;

        pubSubTemplate.ifPresentOrElse(
                template -> {
                    template.subscribe(SUBSCRIPTION, this::handleMessage);
                    log.info("czar-auth subscribed to Pub/Sub subscription: {}", SUBSCRIPTION);
                },
                () -> log.warn("PubSubTemplate not available — czar-auth UserEventSubscriber inactive")
        );
    }

    private void handleMessage(BasicAcknowledgeablePubsubMessage message) {
        String data = message.getPubsubMessage().getData().toStringUtf8();
        try {
            PubSubMessageEnvelope envelope = objectMapper.readValue(data, PubSubMessageEnvelope.class);

            if ("user.deleted".equals(envelope.getEventType())) {
                revokeAllTokens(envelope.getUserId());
            }
            message.ack();
        } catch (Exception e) {
            log.error("czar-auth failed to process Pub/Sub message: {}", e.getMessage(), e);
            message.nack();
        }
    }

    @Transactional
    protected void revokeAllTokens(String userIdStr) {
        UUID userId = UUID.fromString(userIdStr);
        refreshTokenRepository.revokeAllForUser(userId);
        log.info("Revoked all refresh tokens for deleted userId={}", userId);
    }
}
