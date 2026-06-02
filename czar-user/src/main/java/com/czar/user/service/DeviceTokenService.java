package com.czar.user.service;

import com.czar.user.domain.DeviceToken;
import com.czar.user.dto.DeviceTokenRequest;
import com.czar.user.repository.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /** Upsert: one token per user. If a token already exists it is replaced. */
    @Transactional
    public void upsertToken(UUID userId, DeviceTokenRequest request) {
        // Remove any stale token for this user
        deviceTokenRepository.deleteByUserId(userId);

        // Also clean up if the same FCM token belonged to another user (token reassignment)
        deviceTokenRepository.findByFcmToken(request.fcmToken())
                .ifPresent(deviceTokenRepository::delete);

        DeviceToken token = new DeviceToken();
        token.setUserId(userId);
        token.setFcmToken(request.fcmToken());
        token.setPlatform(request.platform());
        deviceTokenRepository.save(token);
    }

    @Transactional
    public void removeToken(UUID userId) {
        deviceTokenRepository.deleteByUserId(userId);
    }
}
