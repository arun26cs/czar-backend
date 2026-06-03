package com.czar.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Real FCM implementation using Firebase Admin SDK.
 * Active only when {@code firebase.enabled=true}.
 * Handles stale token errors by throwing {@link StaleTokenException}.
 */
@Service
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FcmSenderImpl implements FcmSender {

    private static final Logger log = LoggerFactory.getLogger(FcmSenderImpl.class);

    private final FirebaseMessaging messaging;

    public FcmSenderImpl(FirebaseApp firebaseApp) {
        this.messaging = FirebaseMessaging.getInstance(firebaseApp);
    }

    @Override
    public void sendPush(String fcmToken, String title, String body) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();
        try {
            String response = messaging.send(message);
            log.info("FCM push sent: {}", response);
        } catch (FirebaseMessagingException ex) {
            if (MessagingErrorCode.UNREGISTERED.equals(ex.getMessagingErrorCode())) {
                log.warn("Stale FCM token detected: {}", fcmToken);
                throw new StaleTokenException(fcmToken);
            }
            log.error("FCM send failed for token={}: {}", fcmToken, ex.getMessage());
        }
    }
}
