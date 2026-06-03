package com.czar.user.controller;

import com.czar.user.repository.DeviceTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal service-to-service endpoint for czar-notification to fetch a user's
 * FCM device token. Protected by X-Service-Token header (no JWT required).
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalUserController {

    @Value("${czar.internal.service-token}")
    private String expectedToken;

    private final DeviceTokenRepository deviceTokenRepository;

    public InternalUserController(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    public record DeviceTokenInfo(String fcmToken, String platform) {}

    @GetMapping("/device-tokens")
    public ResponseEntity<DeviceTokenInfo> getDeviceToken(
            @RequestHeader("X-Service-Token") String token,
            @RequestParam UUID userId) {

        if (!expectedToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return deviceTokenRepository.findByUserId(userId)
                .map(dt -> ResponseEntity.ok(new DeviceTokenInfo(dt.getFcmToken(), dt.getPlatform())))
                .orElse(ResponseEntity.notFound().<DeviceTokenInfo>build());
    }
}
