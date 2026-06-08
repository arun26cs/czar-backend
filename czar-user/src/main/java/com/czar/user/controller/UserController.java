package com.czar.user.controller;

import com.czar.user.dto.UserProfileResponse;
import com.czar.user.dto.UserProfileUpdateRequest;
import com.czar.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PatchMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            Authentication auth,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    /**
     * DELETE /api/v1/users/me
     * Soft-deletes the account. Required by Apple App Store review guidelines.
     * Removes device tokens immediately; Pub/Sub event triggers downstream cleanup.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(Authentication auth) {
        userService.deleteAccount(UUID.fromString(auth.getName()));
        return ResponseEntity.noContent().build();
    }
}
