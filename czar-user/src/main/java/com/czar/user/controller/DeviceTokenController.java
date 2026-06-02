package com.czar.user.controller;

import com.czar.user.dto.DeviceTokenRequest;
import com.czar.user.service.DeviceTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/device-token")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @PostMapping
    public ResponseEntity<Void> upsertToken(
            Authentication auth,
            @Valid @RequestBody DeviceTokenRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        deviceTokenService.upsertToken(userId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> removeToken(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        deviceTokenService.removeToken(userId);
        return ResponseEntity.noContent().build();
    }
}
