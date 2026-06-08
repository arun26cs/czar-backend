package com.czar.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Fix 4 — WebSocketEventBroadcaster unit tests.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketEventBroadcasterTest {

    @Mock
    private WebSocketSessionRegistry registry;

    private WebSocketEventBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new WebSocketEventBroadcaster(registry, new ObjectMapper());
    }

    @Test
    void sendToUser_openSession_sendsJsonMessage() {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mock(WebSocketSession.class);
        WebSocketMessage message = mock(WebSocketMessage.class);

        when(registry.getSessions(userId)).thenReturn(List.of(session));
        when(session.isOpen()).thenReturn(true);
        when(session.textMessage(anyString())).thenReturn(message);
        when(session.send(any())).thenReturn(Mono.empty());

        broadcaster.sendToUser(userId, Map.of("eventType", "AI_RESULT_READY", "jobId", "abc"));

        verify(session).send(any());
    }

    @Test
    void sendToUser_closedSession_skipsWithoutSending() {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mock(WebSocketSession.class);

        when(registry.getSessions(userId)).thenReturn(List.of(session));
        when(session.isOpen()).thenReturn(false);

        broadcaster.sendToUser(userId, Map.of("eventType", "PING"));

        verify(session, never()).send(any());
    }

    @Test
    void sendToUser_noSessions_doesNothing() {
        UUID userId = UUID.randomUUID();
        when(registry.getSessions(userId)).thenReturn(List.of());

        broadcaster.sendToUser(userId, Map.of("eventType", "PING"));

        // No interaction with any session
        verifyNoMoreInteractions(registry);
    }

    @Test
    void sendToUser_mixedOpenAndClosedSessions_onlySendsToOpen() {
        UUID userId = UUID.randomUUID();
        WebSocketSession openSession   = mock(WebSocketSession.class);
        WebSocketSession closedSession = mock(WebSocketSession.class);
        WebSocketMessage message = mock(WebSocketMessage.class);

        when(registry.getSessions(userId)).thenReturn(List.of(openSession, closedSession));
        when(openSession.isOpen()).thenReturn(true);
        when(closedSession.isOpen()).thenReturn(false);
        when(openSession.textMessage(anyString())).thenReturn(message);
        when(openSession.send(any())).thenReturn(Mono.empty());

        broadcaster.sendToUser(userId, Map.of("eventType", "CONFLICT_DETECTED"));

        verify(openSession).send(any());
        verify(closedSession, never()).send(any());
    }
}
