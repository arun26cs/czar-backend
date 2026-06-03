package com.czar.notification.service;

/** Thrown when Firebase reports that a device token is no longer registered. */
public class StaleTokenException extends RuntimeException {

    private final String fcmToken;

    public StaleTokenException(String fcmToken) {
        super("Stale FCM token: " + fcmToken);
        this.fcmToken = fcmToken;
    }

    public String getFcmToken() {
        return fcmToken;
    }
}
