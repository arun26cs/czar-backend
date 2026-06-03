package com.czar.voiceai.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.czar.voiceai.dto.ParsedItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Publishes ai.result.ready events to czar-ai-results Pub/Sub topic.
 * No-op when PubSubTemplate is unavailable (tests / no emulator).
 */
@Component
public class VoiceResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(VoiceResultPublisher.class);
    private static final String TOPIC = "czar-ai-results";

    private final Optional<PubSubTemplate> pubSubTemplate;
    private final ObjectMapper objectMapper;

    public VoiceResultPublisher(Optional<PubSubTemplate> pubSubTemplate, ObjectMapper objectMapper) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String eventType, String userId, String jobId, List<ParsedItem> items) {
        pubSubTemplate.ifPresentOrElse(
                template -> {
                    try {
                        String payload = objectMapper.writeValueAsString(
                                Map.of("jobId", jobId, "items", items));

                        PubSubMessageEnvelope envelope = PubSubMessageEnvelope.builder()
                                .eventType(eventType)
                                .userId(userId)
                                .eventId(UUID.randomUUID().toString())
                                .occurredAt(Instant.now())
                                .payload(payload)
                                .build();

                        template.publish(TOPIC, objectMapper.writeValueAsString(envelope));
                        log.debug("Published {} for userId={} jobId={}", eventType, userId, jobId);
                    } catch (Exception e) {
                        log.warn("Failed to publish ai result to Pub/Sub: {}", e.getMessage());
                    }
                },
                () -> log.debug("PubSubTemplate unavailable — skipping publish (eventType={})", eventType)
        );
    }
}
