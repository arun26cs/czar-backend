package com.czar.user.controller;

import com.czar.user.dto.PreferencesResponse;
import com.czar.user.dto.PreferencesUpdateRequest;
import com.czar.user.service.PreferencesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class PreferencesController {

    private final PreferencesService preferencesService;

    public PreferencesController(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public ResponseEntity<PreferencesResponse> getPreferences(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(preferencesService.getPreferences(userId));
    }

    @PatchMapping
    public ResponseEntity<PreferencesResponse> updatePreferences(
            Authentication auth,
            @Valid @RequestBody PreferencesUpdateRequest request) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(preferencesService.updatePreferences(userId, request));
    }
}
