package com.czar.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for mobile OAuth2 code exchange endpoints.
 *
 * The mobile app (React Native) collects the OAuth2 authorization code
 * via the system browser / deep link and sends it here.
 * The backend exchanges it for user tokens without Spring Security's redirect flow.
 */
public record MobileOAuthRequest(
        @NotBlank String code,
        @NotBlank String redirectUri) {
}
