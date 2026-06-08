package com.czar.gateway.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges GCP Pub/Sub events to connected WebSocket clients.
 *
 * <p>Subscribes to:
 * <ul>
 *   <li>{@code czar-ai-results-gw-sub} — forwards {@code AI_RESULT_READY} events</li>
 *   <li>{@code czar-notifications-gw-sub} — forwards {@code CONFLICT_DETECTED} and other notifications</li>
 * </ul>
 *
 * <p>Only activated when a {@link PubSubTemplate} bean is available (i.e. GCP Pub/Sub emulator
 * or real GCP Pub/Sub is configured). Skipped entirely in local dev without emulator.
 */
@Component
@ConditionalOnBean(PubSubTemplate.class)
public class WebSocketPubSubBridge {

    private static final Logger log = LoggerFactory.getLogger(WebSocketPubSubBridge.class);

    private static final String AI_RESULTS_SUBSCRIPTION    = "czar-ai-results-gw-sub";
    private static final String NOTIFICATIONS_SUBSCRIPTION = "czar-notifications-gw-sub";

    private final WebSocketEventBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public WebSocketPubSubBridge(PubSubTemplate pubSubTemplate,
                                  WebSocketEventBroadcaster broadcaster,
                                  ObjectMapper objectMapper) {
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;

        pubSubTemplate.subscribe(AI_RESULTS_SUBSCRIPTION,    this::handleAiResult);
        pubSubTemplate.subscribe(NOTIFICATIONS_SUBSCRIPTION, this::handleNotification);
        log.info("WebSocketPubSubBridge subscribed to {} and {}", AI_RESULTS_SUBSCRIPTION, NOTIFICATIONS_SUBSCRIPTION);
    }

    // ── AI results ─────────────────────────────────────────────────────────

    private void handleAiResult(BasicAcknowledgeablePubsubMessage message) {
        String data = message.getPubsubMessage().getData().toStringUtf8();
        try {
            JsonNode root = objectMapper.readTree(data);
            String userIdStr = root.path("userId").asText(null);
            if (userIdStr == null) {
                message.ack();
                return;
            }
            UUID userId = UUID.fromString(userIdStr);

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", "AI_RESULT_READY");
            event.put("jobId",     root.path("jobId").asText(null));

            // Forward raw items array if present
            if (root.has("items")) {
                event.put("items", objectMapper.convertValue(root.get("items"), List.class));
            }

            broadcaster.sendToUser(userId, event);
            message.ack();
        } catch (Exception ex) {
            log.error("WebSocket bridge failed to handle AI result: {}", ex.getMessage());
            message.nack();
        }
    }

    // ── Notifications (conflict alerts, plan status updates) ────────────────

    private void handleNotification(BasicAcknowledgeablePubsubMessage message) {
        String data = message.getPubsubMessage().getData().toStringUtf8();
        try {
            JsonNode root = objectMapper.readTree(data);
            String userIdStr = root.path("userId").asText(null);
            String type      = root.path("type").asText(null);
            if (userIdStr == null || type == null) {
                message.ack();
                return;
            }
            UUID userId = UUID.fromString(userIdStr);

            Map<String, Object> event = buildNotificationEvent(type, root);
            if (event != null) {
                broadcaster.sendToUser(userId, event);
            }
            message.ack();
        } catch (Exception ex) {
            log.error("WebSocket bridge failed to handle notification: {}", ex.getMessage());
            message.nack();
        }
    }

    private Map<String, Object> buildNotificationEvent(String type, JsonNode root) {
        return switch (type) {
            case "conflict_alert" -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("eventType", "CONFLICT_DETECTED");
                e.put("planIds",   objectMapper.convertValue(root.path("planIds"), List.class));
                e.put("message",   root.path("message").asText("Schedule conflict detected"));
                yield e;
            }
            case "plan_status_updated" -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("eventType", "PLAN_STATUS_UPDATED");
                e.put("planId",    root.path("planId").asText(null));
                e.put("newStatus", root.path("newStatus").asText(null));
                yield e;
            }
            default -> {
                log.debug("WebSocket bridge: no WS mapping for notification type={}", type);
                yield null;
            }
        };
    }
}
