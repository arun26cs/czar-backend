package com.czar.notification.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.czar.notification.dto.NotificationPayload;
import com.czar.notification.service.NotificationDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Subscribes to the czar-notifications Pub/Sub topic.
 * On any notification event: delegates to {@link NotificationDispatcher} for FCM delivery.
 */
@Component
public class NotificationEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventSubscriber.class);
    private static final String SUBSCRIPTION = "czar-notifications-sub";

    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public NotificationEventSubscriber(
            Optional<PubSubTemplate> pubSubTemplate,
            NotificationDispatcher dispatcher,
            ObjectMapper objectMapper) {

        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;

        pubSubTemplate.ifPresentOrElse(
                template -> {
                    template.subscribe(SUBSCRIPTION, this::handleRawMessage);
                    log.info("Subscribed to Pub/Sub subscription: {}", SUBSCRIPTION);
                },
                () -> log.warn("PubSubTemplate not available — NotificationEventSubscriber inactive (local dev)")
        );
    }

    private void handleRawMessage(BasicAcknowledgeablePubsubMessage message) {
        String data = message.getPubsubMessage().getData().toStringUtf8();
        try {
            PubSubMessageEnvelope envelope = objectMapper.readValue(data, PubSubMessageEnvelope.class);
            processEnvelope(envelope);
            message.ack();
        } catch (Exception e) {
            log.error("Failed to process notification event: {}", e.getMessage(), e);
            message.nack();
        }
    }

    /**
     * Package-private so tests can call it directly without going through Pub/Sub.
     */
    void processEnvelope(PubSubMessageEnvelope envelope) throws Exception {
        String userId = envelope.getUserId();
        NotificationPayload payload = objectMapper.readValue(
                envelope.getPayload(), NotificationPayload.class);
        dispatcher.dispatch(userId, payload);
    }
}
