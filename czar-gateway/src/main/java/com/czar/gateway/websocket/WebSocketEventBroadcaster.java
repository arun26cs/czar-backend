package com.czar.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Sends server-push events to connected WebSocket clients.
 *
 * <p>Used by {@link WebSocketPubSubBridge} to forward Pub/Sub events, and can be
 * called directly from any gateway component.
 *
 * <p>Event payload follows a simple JSON envelope:
 * <pre>{ "eventType": "AI_RESULT_READY", ... }</pre>
 */
@Component
public class WebSocketEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventBroadcaster.class);

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public WebSocketEventBroadcaster(WebSocketSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a JSON event payload to all active sessions for the given userId.
     * Silently skips if the user has no connected sessions.
     */
    public void sendToUser(UUID userId, Map<String, Object> event) {
        registry.getSessions(userId).forEach(session -> {
            if (!session.isOpen()) return;
            try {
                String json = objectMapper.writeValueAsString(event);
                session.send(Mono.just(session.textMessage(json)))
                        .doOnError(ex -> log.warn("WS send failed for userId={}: {}", userId, ex.getMessage()))
                        .subscribe();
            } catch (Exception ex) {
                log.warn("WS serialisation error for userId={}: {}", userId, ex.getMessage());
            }
        });
    }
}
