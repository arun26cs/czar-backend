package com.czar.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** FCM device token for a user, returned by czar-user's internal endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceTokenInfo(String fcmToken, String platform) {
}
