package com.czar.gateway.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Fix 4 — WebSocketSessionRegistry unit tests.
 *
 * Pure unit test — no Spring context needed.
 */
class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;
    private UUID userId;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
        userId = UUID.randomUUID();
    }

    @Test
    void register_addsSessionForUser() {
        WebSocketSession session = mock(WebSocketSession.class);

        registry.register(userId, session);

        assertThat(registry.getSessions(userId)).containsExactly(session);
    }

    @Test
    void hasSession_returnsFalse_whenNoSessionRegistered() {
        assertThat(registry.hasSession(userId)).isFalse();
    }

    @Test
    void hasSession_returnsTrue_afterRegistration() {
        WebSocketSession session = mock(WebSocketSession.class);
        registry.register(userId, session);

        assertThat(registry.hasSession(userId)).isTrue();
    }

    @Test
    void getSessions_returnsEmptyList_forUnknownUser() {
        assertThat(registry.getSessions(UUID.randomUUID())).isEmpty();
    }

    @Test
    void register_multipleSessionsForSameUser_allReturned() {
        WebSocketSession s1 = mock(WebSocketSession.class);
        WebSocketSession s2 = mock(WebSocketSession.class);

        registry.register(userId, s1);
        registry.register(userId, s2);

        List<WebSocketSession> sessions = registry.getSessions(userId);
        assertThat(sessions).hasSize(2).containsExactlyInAnyOrder(s1, s2);
    }

    @Test
    void deregister_removesSpecificSession() {
        WebSocketSession s1 = mock(WebSocketSession.class);
        WebSocketSession s2 = mock(WebSocketSession.class);
        registry.register(userId, s1);
        registry.register(userId, s2);

        registry.deregister(userId, s1);

        assertThat(registry.getSessions(userId)).containsExactly(s2);
    }

    @Test
    void deregister_lastSession_removesUserEntry() {
        WebSocketSession session = mock(WebSocketSession.class);
        registry.register(userId, session);

        registry.deregister(userId, session);

        assertThat(registry.hasSession(userId)).isFalse();
        assertThat(registry.getSessions(userId)).isEmpty();
    }

    @Test
    void deregister_unknownUser_doesNotThrow() {
        WebSocketSession session = mock(WebSocketSession.class);
        assertThatNoException().isThrownBy(() -> registry.deregister(userId, session));
    }

    @Test
    void getSessions_returnsImmutableSnapshot() {
        WebSocketSession session = mock(WebSocketSession.class);
        registry.register(userId, session);

        List<WebSocketSession> snapshot = registry.getSessions(userId);

        // Snapshot should be immutable
        assertThatThrownBy(() -> snapshot.add(mock(WebSocketSession.class)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
