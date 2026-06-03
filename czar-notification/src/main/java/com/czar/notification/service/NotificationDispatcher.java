package com.czar.notification.service;

import com.czar.notification.dto.DeviceTokenInfo;
import com.czar.notification.dto.NotificationPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Routes an incoming notification event to FCM push delivery.
 *
 * <p>Flow:
 * <ol>
 *   <li>Look up the user's FCM token via {@link UserClient}.
 *   <li>Build a human-readable title and body based on notification type.
 *   <li>Delegate to {@link FcmSender} to deliver the push.
 *   <li>On {@link StaleTokenException}, log a warning (token cleanup is Phase 8 extension).
 * </ol>
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final UserClient userClient;
    private final FcmSender fcmSender;

    public NotificationDispatcher(UserClient userClient, FcmSender fcmSender) {
        this.userClient = userClient;
        this.fcmSender = fcmSender;
    }

    public void dispatch(String userId, NotificationPayload payload) {
        Optional<DeviceTokenInfo> tokenOpt = userClient.getDeviceToken(UUID.fromString(userId));
        if (tokenOpt.isEmpty()) {
            log.warn("No device token found for userId={} — push skipped (type={})",
                    userId, payload.type());
            return;
        }

        String fcmToken = tokenOpt.get().fcmToken();
        String title = buildTitle(payload);
        String body  = buildBody(payload);

        try {
            fcmSender.sendPush(fcmToken, title, body);
            log.info("Push dispatched to userId={} type={}", userId, payload.type());
        } catch (StaleTokenException ex) {
            log.warn("Stale token for userId={} — token should be removed: {}", userId, ex.getFcmToken());
            // Token cleanup delegated to czar-user; will be removed on next device registration
        }
    }

    private String buildTitle(NotificationPayload payload) {
        return switch (payload.type()) {
            case "conflict_alert"   -> "Schedule Conflict";
            case "plan_reminder"    -> "Plan Reminder";
            case "missed_plan"      -> "Missed Plan";
            case "ai_result_ready"  -> "Voice Parse Complete";
            case "note_saved"       -> "Note Saved";
            default                 -> "Czar Notification";
        };
    }

    private String buildBody(NotificationPayload payload) {
        if (payload.message() != null && !payload.message().isBlank()) {
            return payload.message();
        }
        return switch (payload.type()) {
            case "conflict_alert"  -> "You have overlapping plans. Tap to review.";
            case "plan_reminder"   -> "You have an upcoming plan starting soon.";
            case "missed_plan"     -> "You missed a scheduled plan.";
            case "ai_result_ready" -> "Your voice items are ready to review.";
            case "note_saved"      -> "Your voice note has been saved.";
            default                -> "You have a new notification.";
        };
    }
}
