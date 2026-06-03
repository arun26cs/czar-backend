package com.czar.notification.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.czar.notification.dto.NotificationPayload;
import com.czar.notification.service.NotificationDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventSubscriberTest {

    @Mock NotificationDispatcher dispatcher;

    private NotificationEventSubscriber subscriber;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() {
        subscriber = new NotificationEventSubscriber(
                Optional.empty(),   // no PubSub in tests
                dispatcher,
                objectMapper
        );
    }

    private PubSubMessageEnvelope envelope(String type, String message) throws Exception {
        NotificationPayload payload = new NotificationPayload(
                type, List.of("plan-1", "plan-2"), message, null);
        return PubSubMessageEnvelope.builder()
                .eventType(type)
                .userId(USER_ID)
                .eventId(UUID.randomUUID().toString())
                .occurredAt(Instant.now())
                .payload(objectMapper.writeValueAsString(payload))
                .build();
    }

    @Test
    void conflictAlertEnvelope_dispatchesWithCorrectUserId() throws Exception {
        subscriber.processEnvelope(envelope("conflict_alert", "Plans overlap"));

        ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(dispatcher).dispatch(eq(USER_ID), captor.capture());

        NotificationPayload dispatched = captor.getValue();
        assertThat(dispatched.type()).isEqualTo("conflict_alert");
        assertThat(dispatched.message()).isEqualTo("Plans overlap");
    }

    @Test
    void planReminderEnvelope_dispatchesPlanReminder() throws Exception {
        subscriber.processEnvelope(envelope("plan_reminder", "Standup in 10 min"));

        ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(dispatcher).dispatch(eq(USER_ID), captor.capture());

        assertThat(captor.getValue().type()).isEqualTo("plan_reminder");
    }

    @Test
    void aiResultReadyEnvelope_dispatchesCorrectly() throws Exception {
        NotificationPayload payload = new NotificationPayload(
                "ai_result_ready", List.of(), null, "job-abc-123");
        PubSubMessageEnvelope env = PubSubMessageEnvelope.builder()
                .eventType("ai_result_ready")
                .userId(USER_ID)
                .eventId(UUID.randomUUID().toString())
                .occurredAt(Instant.now())
                .payload(objectMapper.writeValueAsString(payload))
                .build();

        subscriber.processEnvelope(env);

        ArgumentCaptor<NotificationPayload> captor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(dispatcher).dispatch(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().jobId()).isEqualTo("job-abc-123");
    }
}
