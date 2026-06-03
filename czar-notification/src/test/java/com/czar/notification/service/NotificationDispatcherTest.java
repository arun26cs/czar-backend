package com.czar.notification.service;

import com.czar.notification.dto.DeviceTokenInfo;
import com.czar.notification.dto.NotificationPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock UserClient userClient;
    @Mock FcmSender fcmSender;
    @InjectMocks NotificationDispatcher dispatcher;

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final DeviceTokenInfo TOKEN = new DeviceTokenInfo("fcm-token-abc", "android");

    private NotificationPayload payload(String type, String message) {
        return new NotificationPayload(type, List.of(), message, null);
    }

    @Test
    void conflictAlert_withDeviceToken_sendsPush() {
        when(userClient.getDeviceToken(any())).thenReturn(Optional.of(TOKEN));

        dispatcher.dispatch(USER_ID, new NotificationPayload(
                "conflict_alert",
                List.of("plan-1", "plan-2"),
                "Run conflicts with Standup",
                null));

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor  = ArgumentCaptor.forClass(String.class);
        verify(fcmSender).sendPush(eq("fcm-token-abc"), titleCaptor.capture(), bodyCaptor.capture());

        assertThat(titleCaptor.getValue()).isEqualTo("Schedule Conflict");
        assertThat(bodyCaptor.getValue()).isEqualTo("Run conflicts with Standup");
    }

    @Test
    void conflictAlert_noDeviceToken_noSend() {
        when(userClient.getDeviceToken(any())).thenReturn(Optional.empty());

        dispatcher.dispatch(USER_ID, payload("conflict_alert", "conflict"));

        verifyNoInteractions(fcmSender);
    }

    @Test
    void planReminder_sendsPushWithDefaultBody() {
        when(userClient.getDeviceToken(any())).thenReturn(Optional.of(TOKEN));

        dispatcher.dispatch(USER_ID, payload("plan_reminder", null));

        verify(fcmSender).sendPush(anyString(), eq("Plan Reminder"),
                eq("You have an upcoming plan starting soon."));
    }

    @Test
    void missedPlan_sendsPush() {
        when(userClient.getDeviceToken(any())).thenReturn(Optional.of(TOKEN));

        dispatcher.dispatch(USER_ID, payload("missed_plan", null));

        verify(fcmSender).sendPush(anyString(), eq("Missed Plan"), anyString());
    }

    @Test
    void aiResultReady_sendsPush() {
        when(userClient.getDeviceToken(any())).thenReturn(Optional.of(TOKEN));

        dispatcher.dispatch(USER_ID, new NotificationPayload(
                "ai_result_ready", List.of(), null, "job-123"));

        verify(fcmSender).sendPush(anyString(), eq("Voice Parse Complete"), anyString());
    }

    @Test
    void noteSaved_sendsPush() {
        when(userClient.getDeviceToken(any())).thenReturn(Optional.of(TOKEN));

        dispatcher.dispatch(USER_ID, payload("note_saved", "Note saved successfully"));

        verify(fcmSender).sendPush(anyString(), eq("Note Saved"), eq("Note saved successfully"));
    }

    @Test
    void unknownType_sendsGenericTitle() {
        when(userClient.getDeviceToken(any())).thenReturn(Optional.of(TOKEN));

        dispatcher.dispatch(USER_ID, payload("custom_event", null));

        verify(fcmSender).sendPush(anyString(), eq("Czar Notification"), anyString());
    }

    @Test
    void staleToken_logsWarnDoesNotRethrow() {
        when(userClient.getDeviceToken(any())).thenReturn(Optional.of(TOKEN));
        doThrow(new StaleTokenException("fcm-token-abc"))
                .when(fcmSender).sendPush(anyString(), anyString(), anyString());

        // Should not throw — stale token is logged and swallowed
        dispatcher.dispatch(USER_ID, payload("conflict_alert", "conflict"));
    }
}
