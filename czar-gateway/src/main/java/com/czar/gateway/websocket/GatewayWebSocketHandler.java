package com.czar.gateway.websocket;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Reactive WebSocket handler for real-time server-push events.
 *
 * <p>Connection URL: {@code ws://host/ws?token=eyJ...}
 * <ul>
 *   <li>JWT is validated from the {@code token} query parameter.</li>
 *   <li>Session is registered in {@link WebSocketSessionRegistry}.</li>
 *   <li>A heartbeat ping is sent every 30 seconds.</li>
 *   <li>Session is deregistered on disconnect.</li>
 * </ul>
 *
 * <p>Events pushed to the client:
 * <pre>
 * { "eventType": "AI_RESULT_READY",    "jobId": "uuid", "items": [...] }
 * { "eventType": "CONFLICT_DETECTED",  "planIds": ["a","b"], "message": "..." }
 * { "eventType": "PLAN_STATUS_UPDATED","planId": "uuid", "newStatus": "missed" }
 * </pre>
 *
 * <p>When {@code jwt.public-key-path} is not set (local dev / tests), the token
 * query param is still read but signature validation is skipped — the sub claim
 * is used directly.
 */
@Component
public class GatewayWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketHandler.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final WebSocketSessionRegistry registry;
    private final WebSocketEventBroadcaster broadcaster;

    @Autowired(required = false)
    private RSAPublicKey rsaPublicKey;

    public GatewayWebSocketHandler(WebSocketSessionRegistry registry,
                                   WebSocketEventBroadcaster broadcaster) {
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Extract JWT from ?token= query param
        String query = session.getHandshakeInfo().getUri().getQuery();
        String token = extractToken(query);

        UUID userId = resolveUserId(token);
        if (userId == null) {
            log.warn("WebSocket connection rejected — invalid or missing token");
            return session.close();
        }

        log.info("WebSocket connected: userId={} sessionId={}", userId, session.getId());
        registry.register(userId, session);

        // Heartbeat: send {"eventType":"PING"} every 30 seconds to keep connection alive
        Mono<Void> heartbeat = Mono.delay(HEARTBEAT_INTERVAL)
                .repeat()
                .flatMap(tick -> {
                    if (!session.isOpen()) return Mono.empty();
                    broadcaster.sendToUser(userId, Map.of("eventType", "PING"));
                    return Mono.empty();
                })
                .then();

        // Inbound: consume (and discard) any messages from client (pong frames, etc.)
        Mono<Void> inbound = session.receive()
                .doOnNext(msg -> log.debug("WS inbound from userId={}: {}", userId, msg.getPayloadAsText()))
                .then();

        // Deregister on disconnect
        Mono<Void> cleanup = Mono.fromRunnable(() -> {
            registry.deregister(userId, session);
            log.info("WebSocket disconnected: userId={} sessionId={}", userId, session.getId());
        });

        return Mono.zip(inbound, heartbeat)
                .then()
                .doFinally(signal -> cleanup.subscribe());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String extractToken(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring("token=".length());
            }
        }
        return null;
    }

    private UUID resolveUserId(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            // Validate signature when RSA key is present (production)
            if (rsaPublicKey != null) {
                com.nimbusds.jose.crypto.RSASSAVerifier verifier =
                        new com.nimbusds.jose.crypto.RSASSAVerifier(rsaPublicKey);
                if (!jwt.verify(verifier)) {
                    log.warn("WebSocket JWT signature verification failed");
                    return null;
                }
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            // Validate expiry
            if (claims.getExpirationTime() != null &&
                    claims.getExpirationTime().toInstant().isBefore(java.time.Instant.now())) {
                log.warn("WebSocket JWT is expired");
                return null;
            }
            String sub = claims.getSubject();
            return UUID.fromString(sub);
        } catch (Exception ex) {
            log.warn("WebSocket JWT parse error: {}", ex.getMessage());
            return null;
        }
    }
}
