package com.czar.gateway.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of active WebSocket sessions, keyed by userId.
 *
 * <p>One user may have multiple concurrent sessions (e.g. phone + tablet).
 * Broadcasting sends to all of them.
 */
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<UUID, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(UUID userId, WebSocketSession session) {
        sessions.compute(userId, (id, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(session);
            return list;
        });
    }

    public void deregister(UUID userId, WebSocketSession session) {
        sessions.computeIfPresent(userId, (id, list) -> {
            list.remove(session);
            return list.isEmpty() ? null : list;
        });
    }

    /** Returns a snapshot of sessions for the given user (may be empty). */
    public List<WebSocketSession> getSessions(UUID userId) {
        List<WebSocketSession> list = sessions.get(userId);
        return list != null ? List.copyOf(list) : List.of();
    }

    public boolean hasSession(UUID userId) {
        List<WebSocketSession> list = sessions.get(userId);
        return list != null && !list.isEmpty();
    }
}
