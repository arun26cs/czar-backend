package com.czar.auth.messaging;

import com.czar.auth.repository.RefreshTokenRepository;
import com.czar.common.messaging.PubSubMessageEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Fix 3 — UserEventSubscriber unit tests.
 *
 * PubSubTemplate is passed as Optional.empty() → no subscription setup in constructor.
 * We test handleMessage directly by constructing BasicAcknowledgeablePubsubMessage stubs.
 */
@ExtendWith(MockitoExtension.class)
class UserEventSubscriberTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private BasicAcknowledgeablePubsubMessage mockMessage;

    private ObjectMapper objectMapper;
    private UserEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Pass empty Optional — no Pub/Sub subscription, but handleMessage can be tested directly
        subscriber = new UserEventSubscriber(Optional.empty(), refreshTokenRepository, objectMapper);
    }

    @Test
    void userDeleted_event_revokesAllRefreshTokens() {
        UUID userId = UUID.randomUUID();
        String json = buildEnvelope("user.deleted", userId.toString());
        stubMessage(json);

        // Invoke via reflection — handleMessage is private but is the message callback
        invokeHandleMessage(json);

        verify(refreshTokenRepository).revokeAllForUser(userId);
        verify(mockMessage).ack();
    }

    @Test
    void userCreated_event_doesNotRevokeTokens() {
        UUID userId = UUID.randomUUID();
        String json = buildEnvelope("user.created", userId.toString());
        stubMessage(json);

        invokeHandleMessage(json);

        verify(refreshTokenRepository, never()).revokeAllForUser(any());
        verify(mockMessage).ack();
    }

    @Test
    void unrelatedEvent_doesNotRevokeTokens() {
        UUID userId = UUID.randomUUID();
        String json = buildEnvelope("plan.created", userId.toString());
        stubMessage(json);

        invokeHandleMessage(json);

        verify(refreshTokenRepository, never()).revokeAllForUser(any());
        verify(mockMessage).ack();
    }

    @Test
    void malformedJson_nacksMessage() {
        String badJson = "{ invalid json }";
        stubMessage(badJson);

        invokeHandleMessage(badJson);

        verify(refreshTokenRepository, never()).revokeAllForUser(any());
        verify(mockMessage).nack();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String buildEnvelope(String eventType, String userId) {
        PubSubMessageEnvelope envelope = PubSubMessageEnvelope.builder()
                .eventType(eventType)
                .userId(userId)
                .build();
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubMessage(String json) {
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(json))
                .build();
        when(mockMessage.getPubsubMessage()).thenReturn(pubsubMessage);
    }

    /**
     * Calls the package-private handleMessage indirectly via revokeAllTokens.
     * Since handleMessage is private, we test its behaviour through revokeAllTokens
     * by invoking it via Spring's ReflectionTestUtils.
     */
    private void invokeHandleMessage(String json) {
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                subscriber, "handleMessage", mockMessage);
    }
}
