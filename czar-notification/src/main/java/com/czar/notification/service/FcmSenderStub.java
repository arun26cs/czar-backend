package com.czar.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * No-op FCM sender used in local dev and tests when Firebase is not configured.
 * Active when {@code firebase.enabled=true} is NOT set (the default).
 */
@Service
@ConditionalOnMissingBean(FcmSenderImpl.class)
public class FcmSenderStub implements FcmSender {

    private static final Logger log = LoggerFactory.getLogger(FcmSenderStub.class);

    @Override
    public void sendPush(String fcmToken, String title, String body) {
        log.info("[FCM STUB] Push to token={} | title={} | body={}", fcmToken, title, body);
    }
}
