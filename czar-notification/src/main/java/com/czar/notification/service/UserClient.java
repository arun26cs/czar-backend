package com.czar.notification.service;

import com.czar.notification.dto.DeviceTokenInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * Fetches a user's FCM device token from czar-user's internal endpoint.
 */
public interface UserClient {

    /**
     * Returns the FCM device token for the given user, or empty if the user
     * has no registered device or czar-user is unreachable.
     */
    Optional<DeviceTokenInfo> getDeviceToken(UUID userId);
}
