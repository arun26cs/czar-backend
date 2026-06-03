package com.czar.conflict.messaging;

import com.czar.common.messaging.PubSubMessageEnvelope;
import com.czar.conflict.dto.ConflictAlertPayload;
import com.czar.conflict.dto.PlanSummary;
import com.czar.conflict.service.ConflictChecker;
import com.czar.conflict.service.PlannerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanEventSubscriberTest {

    @Mock PlannerClient plannerClient;
    @Mock NotificationPublisher notificationPublisher;

    private PlanEventSubscriber subscriber;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final LocalDate DATE = LocalDate.of(2025, 6, 1);

    @BeforeEach
    void setUp() {
        subscriber = new PlanEventSubscriber(
                Optional.empty(),        // no PubSub in tests
                plannerClient,
                new ConflictChecker(),   // use real checker
                notificationPublisher,
                objectMapper
        );
    }

    private PubSubMessageEnvelope envelope(String eventType, String scheduledDate) throws Exception {
        String payload = "{\"planId\":\"" + UUID.randomUUID() + "\""
                + (scheduledDate != null ? ",\"scheduledDate\":\"" + scheduledDate + "\"" : "")
                + "}";
        return PubSubMessageEnvelope.builder()
                .eventType(eventType)
                .userId(USER_ID)
                .eventId(UUID.randomUUID().toString())
                .occurredAt(Instant.now())
                .payload(payload)
                .build();
    }

    private PlanSummary plan(String title, int hour, int minute, int durationMinutes) {
        return new PlanSummary(UUID.randomUUID(), UUID.fromString(USER_ID),
                title, DATE, hour, minute, durationMinutes);
    }

    // -------------------------------------------------------------------------

    @Test
    void planCreated_conflictsFound_publishesAlert() throws Exception {
        // two overlapping plans: 7:00-8:30 and 8:00-9:00
        when(plannerClient.getPlansForDate(any(), any()))
                .thenReturn(List.of(
                        plan("Run",     7, 0, 90),
                        plan("Standup", 8, 0, 60)
                ));

        subscriber.processEnvelope(envelope("plan.created", "2025-06-01"));

        ArgumentCaptor<ConflictAlertPayload> captor = ArgumentCaptor.forClass(ConflictAlertPayload.class);
        verify(notificationPublisher).publishConflictAlert(eq(USER_ID), captor.capture());

        ConflictAlertPayload alert = captor.getValue();
        assertThat(alert.planIds()).hasSize(2);
        assertThat(alert.message()).contains("Run").contains("Standup");
    }

    @Test
    void planCreated_noConflicts_noPublish() throws Exception {
        // two non-overlapping plans
        when(plannerClient.getPlansForDate(any(), any()))
                .thenReturn(List.of(
                        plan("Run",     7, 0, 60),
                        plan("Standup", 9, 0, 60)
                ));

        subscriber.processEnvelope(envelope("plan.created", "2025-06-01"));

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void planUpdated_conflictsFound_publishesAlert() throws Exception {
        when(plannerClient.getPlansForDate(any(), any()))
                .thenReturn(List.of(
                        plan("Meeting A", 10, 0, 60),
                        plan("Meeting B", 10, 30, 60)
                ));

        subscriber.processEnvelope(envelope("plan.updated", "2025-06-01"));

        verify(notificationPublisher).publishConflictAlert(eq(USER_ID), any());
    }

    @Test
    void planDeleted_ignored_noPlannerCall() throws Exception {
        subscriber.processEnvelope(envelope("plan.deleted", "2025-06-01"));

        verifyNoInteractions(plannerClient);
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void missingScheduledDate_skipsPlannerCall() throws Exception {
        subscriber.processEnvelope(envelope("plan.created", null));

        verifyNoInteractions(plannerClient);
        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void singlePlan_noConflictCheck() throws Exception {
        when(plannerClient.getPlansForDate(any(), any()))
                .thenReturn(List.of(plan("Solo", 7, 0, 60)));

        subscriber.processEnvelope(envelope("plan.created", "2025-06-01"));

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void plannerClientFailure_gracefullyHandled() throws Exception {
        when(plannerClient.getPlansForDate(any(), any())).thenReturn(Collections.emptyList());

        subscriber.processEnvelope(envelope("plan.created", "2025-06-01"));

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    void multiplePairs_publishesOneAlertPerPair() throws Exception {
        // A-B and A-C both conflict
        when(plannerClient.getPlansForDate(any(), any()))
                .thenReturn(List.of(
                        plan("A", 7,  0, 120),
                        plan("B", 7, 30, 120),
                        plan("C", 8,  0, 120)
                ));

        subscriber.processEnvelope(envelope("plan.created", "2025-06-01"));

        verify(notificationPublisher, times(3))
                .publishConflictAlert(eq(USER_ID), any());
    }
}
