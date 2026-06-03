package com.czar.notification.service;

/**
 * Sends an FCM push notification to a single device token.
 * Production implementation uses Firebase Admin SDK.
 * Stub implementation logs only (local dev / tests).
 */
public interface FcmSender {

    /**
     * Send a push notification.
     *
     * @param fcmToken device registration token
     * @param title    notification title
     * @param body     notification body text
     */
    void sendPush(String fcmToken, String title, String body);
}
