package com.czar.auth.controller;

import com.czar.auth.dto.MobileOAuthRequest;
import com.czar.auth.dto.TokenPairResponse;
import com.czar.auth.service.MobileOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Mobile-specific OAuth2 endpoints for React Native (and web SPAs).
 *
 * <p>The mobile app collects the OAuth2 authorization code through the system browser
 * and a deep link callback ({@code czar://auth/callback}), then posts the code here.
 * The backend exchanges it with Google/GitHub and returns a Czar JWT pair.
 *
 * <p>These endpoints are PUBLIC (no JWT required) — listed in SecurityConfig permitAll.
 */
@RestController
@RequestMapping("/auth/oauth")
public class MobileOAuthController {

    private final MobileOAuthService mobileOAuthService;

    public MobileOAuthController(MobileOAuthService mobileOAuthService) {
        this.mobileOAuthService = mobileOAuthService;
    }

    /**
     * POST /auth/oauth/google/mobile
     * Body: { "code": "4/xxx...", "redirectUri": "czar://auth/callback" }
     * Response: { accessToken, refreshToken, expiresIn, tokenType }
     */
    @PostMapping("/google/mobile")
    public ResponseEntity<TokenPairResponse> googleMobile(
            @Valid @RequestBody MobileOAuthRequest request,
            HttpServletRequest httpRequest) {
        String deviceHint = httpRequest.getHeader("X-Device-Hint");
        return ResponseEntity.ok(mobileOAuthService.googleMobileLogin(request, deviceHint));
    }

    /**
     * POST /auth/oauth/github/mobile
     * Body: { "code": "xxx", "redirectUri": "czar://auth/callback" }
     * Response: { accessToken, refreshToken, expiresIn, tokenType }
     */
    @PostMapping("/github/mobile")
    public ResponseEntity<TokenPairResponse> githubMobile(
            @Valid @RequestBody MobileOAuthRequest request,
            HttpServletRequest httpRequest) {
        String deviceHint = httpRequest.getHeader("X-Device-Hint");
        return ResponseEntity.ok(mobileOAuthService.githubMobileLogin(request, deviceHint));
    }
}
