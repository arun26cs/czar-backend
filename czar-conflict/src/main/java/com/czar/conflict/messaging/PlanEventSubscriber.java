package com.czar.conflict.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.czar.conflict.dto.ConflictAlertPayload;
import com.czar.conflict.dto.PlanSummary;
import com.czar.conflict.service.ConflictChecker;
import com.czar.conflict.service.PlannerClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subscribes to the czar-plan-events Pub/Sub topic.
 *
 * <p>On plan.created / plan.updated:
 * <ol>
 *   <li>Parse the event envelope to extract userId and scheduledDate.
 *   <li>Fetch all plans for that user/date via czar-planner's internal endpoint.
 *   <li>Run O(n²) overlap detection.
 *   <li>Publish a conflict_alert to czar-notifications for each conflicting pair.
 * </ol>
 */
@Component
public class PlanEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(PlanEventSubscriber.class);
    private static final String SUBSCRIPTION = "czar-plan-events-conflict-sub";

    private final PlannerClient plannerClient;
    private final ConflictChecker conflictChecker;
    private final NotificationPublisher notificationPublisher;
    private final ObjectMapper objectMapper;

    public PlanEventSubscriber(
            Optional<PubSubTemplate> pubSubTemplate,
            PlannerClient plannerClient,
            ConflictChecker conflictChecker,
            NotificationPublisher notificationPublisher,
            ObjectMapper objectMapper) {

        this.plannerClient = plannerClient;
        this.conflictChecker = conflictChecker;
        this.notificationPublisher = notificationPublisher;
        this.objectMapper = objectMapper;

        pubSubTemplate.ifPresentOrElse(
                template -> {
                    template.subscribe(SUBSCRIPTION, this::handleRawMessage);
                    log.info("Subscribed to Pub/Sub subscription: {}", SUBSCRIPTION);
                },
                () -> log.warn("PubSubTemplate not available — PlanEventSubscriber inactive (local dev)")
        );
    }

    private void handleRawMessage(BasicAcknowledgeablePubsubMessage message) {
        String data = message.getPubsubMessage().getData().toStringUtf8();
        try {
            PubSubMessageEnvelope envelope = objectMapper.readValue(data, PubSubMessageEnvelope.class);
            processEnvelope(envelope);
            message.ack();
        } catch (Exception e) {
            log.error("Failed to process plan event: {}", e.getMessage(), e);
            message.nack();
        }
    }

    /**
     * Package-private so tests can call it directly without going through Pub/Sub.
     */
    void processEnvelope(PubSubMessageEnvelope envelope) throws Exception {
        String eventType = envelope.getEventType();
        if (!"plan.created".equals(eventType) && !"plan.updated".equals(eventType)) {
            log.debug("Ignoring event type: {}", eventType);
            return;
        }

        String userId = envelope.getUserId();
        JsonNode payload = objectMapper.readTree(envelope.getPayload());
        JsonNode scheduledDateNode = payload.get("scheduledDate");

        if (scheduledDateNode == null || scheduledDateNode.isNull()) {
            log.warn("plan event missing scheduledDate — skipping conflict check for userId={}", userId);
            return;
        }

        LocalDate date = LocalDate.parse(scheduledDateNode.asText());
        List<PlanSummary> plans = plannerClient.getPlansForDate(UUID.fromString(userId), date);

        if (plans.size() < 2) {
            return; // not enough plans to conflict
        }

        List<List<PlanSummary>> conflicts = conflictChecker.detectConflicts(plans);
        for (List<PlanSummary> pair : conflicts) {
            List<String> planIds = pair.stream()
                    .map(p -> p.id().toString())
                    .collect(Collectors.toList());
            String message = buildConflictMessage(pair);
            notificationPublisher.publishConflictAlert(userId, new ConflictAlertPayload(planIds, message));
        }

        if (!conflicts.isEmpty()) {
            log.info("Detected {} conflict pair(s) for userId={} on {}", conflicts.size(), userId, date);
        }
    }

    private String buildConflictMessage(List<PlanSummary> pair) {
        return String.format("Scheduling conflict: \"%s\" and \"%s\" overlap on %s",
                pair.get(0).title(), pair.get(1).title(), pair.get(0).scheduledDate());
    }
}
