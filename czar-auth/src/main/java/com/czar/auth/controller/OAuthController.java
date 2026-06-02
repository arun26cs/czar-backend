package com.czar.auth.controller;

import com.czar.auth.dto.TokenPairResponse;
import com.czar.auth.service.OAuthUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OAuth2 callback handlers for Google and GitHub.
 *
 * Spring Security handles the redirect to the provider and code exchange automatically.
 * These endpoints are called by Spring Security after it populates the OAuth2User.
 * We use a custom success handler (see SecurityConfig) that delegates here,
 * so these are NOT standard @GetMapping callbacks — they receive the resolved OAuth2User.
 */
@RestController
@RequestMapping("/auth/oauth")
public class OAuthController {

    private final OAuthUserService oauthUserService;

    public OAuthController(OAuthUserService oauthUserService) {
        this.oauthUserService = oauthUserService;
    }

    /**
     * Called by the OAuth2 success handler after Google callback.
     * Extracts sub (Google's stable user ID) and email from the ID token claims.
     */
    public TokenPairResponse handleGoogleSuccess(OAuth2AuthenticationToken token,
                                                  HttpServletRequest request) {
        OAuth2User user = token.getPrincipal();
        String providerUserId = user.getAttribute("sub");
        String email = user.getAttribute("email");
        String accessToken = extractAccessToken(token);
        String deviceHint = request.getHeader("X-Device-Hint");
        return oauthUserService.handleOAuthLogin("google", providerUserId, email, accessToken, deviceHint);
    }

    /**
     * Called by the OAuth2 success handler after GitHub callback.
     * GitHub returns 'id' (integer) as stable user ID; email may require a separate call.
     */
    public TokenPairResponse handleGithubSuccess(OAuth2AuthenticationToken token,
                                                   HttpServletRequest request) {
        OAuth2User user = token.getPrincipal();
        // GitHub user id is an Integer — convert to string
        Object idAttr = user.getAttribute("id");
        String providerUserId = idAttr != null ? idAttr.toString() : null;
        String email = resolveGithubEmail(user);
        String accessToken = extractAccessToken(token);
        String deviceHint = request.getHeader("X-Device-Hint");
        return oauthUserService.handleOAuthLogin("github", providerUserId, email, accessToken, deviceHint);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String extractAccessToken(OAuth2AuthenticationToken token) {
        // The access token is available from the client's authorized client in the security context.
        // For now we store a placeholder; token encryption is done in Phase 9.
        return "oauth2_token_stored_via_authorized_client";
    }

    @SuppressWarnings("unchecked")
    private String resolveGithubEmail(OAuth2User user) {
        // Primary email attribute (returned when email is public)
        String email = user.getAttribute("email");
        if (email != null && !email.isBlank()) return email;

        // GitHub may return a list of emails in the 'emails' attribute
        Object emailsAttr = user.getAttribute("emails");
        if (emailsAttr instanceof List<?> emails) {
            return emails.stream()
                    .filter(e -> e instanceof Map)
                    .map(e -> (Map<String, Object>) e)
                    .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .orElse(null);
        }

        // Fallback: GitHub email is private — use noreply address GitHub generates
        // Format: <id>+<login>@users.noreply.github.com
        Object idAttr = user.getAttribute("id");
        String login = user.getAttribute("login");
        if (idAttr != null && login != null) {
            return idAttr + "+" + login + "@users.noreply.github.com";
        }

        throw new IllegalArgumentException("No email available from GitHub account");
    }
}
