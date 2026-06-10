package com.czar.auth.service;

import com.czar.auth.config.JwtProperties;
import com.czar.auth.dto.MobileOAuthRequest;
import com.czar.auth.dto.TokenPairResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Exchanges an OAuth2 authorization code from a mobile app for a Czar JWT pair.
 *
 * <p>Mobile apps cannot use server-side redirect callbacks (they use deep links instead).
 * This service manually performs the code→token exchange with Google / GitHub,
 * fetches the user profile, then delegates to {@link OAuthUserService} for
 * user resolution and JWT issuance.
 *
 * <p>The {@code redirectUri} in the request must match the one registered in the provider
 * console and must be one of the allowed redirect URIs:
 * {@code czar://auth/callback} or {@code https://auth.expo.io/@arun26cs/czar-app}.
 */
@Service
public class MobileOAuthService {

    private static final Logger log = LoggerFactory.getLogger(MobileOAuthService.class);

    private static final java.util.Set<String> ALLOWED_MOBILE_REDIRECT_URIS = java.util.Set.of(
            "czar://auth/callback",
            "https://auth.expo.io/@arun26cs/czar-app"
    );

    private static final String GOOGLE_TOKEN_URL  = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private static final String GITHUB_TOKEN_URL  = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_URL   = "https://api.github.com/user";
    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    private final OAuthUserService oauthUserService;
    private final RestTemplate restTemplate;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String githubClientId;

    @Value("${spring.security.oauth2.client.registration.github.client-secret}")
    private String githubClientSecret;

    public MobileOAuthService(OAuthUserService oauthUserService) {
        this.oauthUserService = oauthUserService;
        this.restTemplate = new RestTemplate();
    }

    // ── Google ────────────────────────────────────────────────────────────────

    /**
     * Exchanges a Google authorization code for a Czar JWT pair.
     */
    public TokenPairResponse googleMobileLogin(MobileOAuthRequest request, String deviceHint) {
        validateRedirectUri(request.redirectUri());

        // Step 1: exchange code for Google access token + id_token
        Map<String, Object> tokenResponse = exchangeGoogleCode(request.code(), request.redirectUri());

        String googleAccessToken = (String) tokenResponse.get("access_token");
        if (googleAccessToken == null) {
            throw new IllegalStateException("Google token exchange failed — no access_token in response");
        }

        // Step 2: fetch user info from Google
        Map<String, Object> userInfo = fetchGoogleUserInfo(googleAccessToken);
        String sub   = (String) userInfo.get("sub");
        String email = (String) userInfo.get("email");

        if (sub == null || email == null) {
            throw new IllegalStateException("Google userinfo missing sub or email");
        }

        return oauthUserService.handleOAuthLogin("google", sub, email, googleAccessToken, deviceHint);
    }

    // ── GitHub ────────────────────────────────────────────────────────────────

    /**
     * Exchanges a GitHub authorization code for a Czar JWT pair.
     */
    public TokenPairResponse githubMobileLogin(MobileOAuthRequest request, String deviceHint) {
        validateRedirectUri(request.redirectUri());

        // Step 1: exchange code for GitHub access token
        String githubAccessToken = exchangeGithubCode(request.code(), request.redirectUri());

        // Step 2: fetch user profile
        Map<String, Object> userProfile = fetchGithubUser(githubAccessToken);
        Object idAttr = userProfile.get("id");
        String providerUserId = idAttr != null ? idAttr.toString() : null;

        // Step 3: resolve email (may require separate /user/emails call)
        String email = resolveGithubEmail(userProfile, githubAccessToken);

        if (providerUserId == null || email == null) {
            throw new IllegalStateException("GitHub user profile missing id or verified email");
        }

        return oauthUserService.handleOAuthLogin("github", providerUserId, email, githubAccessToken, deviceHint);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateRedirectUri(String redirectUri) {
        if (!ALLOWED_MOBILE_REDIRECT_URIS.contains(redirectUri)) {
            throw new IllegalArgumentException(
                    "Invalid redirectUri — must be one of: " + ALLOWED_MOBILE_REDIRECT_URIS);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeGoogleCode(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code",          code);
        params.add("client_id",     googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri",  redirectUri);
        params.add("grant_type",    "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                GOOGLE_TOKEN_URL, new HttpEntity<>(params, headers), Map.class);
        return resp.getBody() != null ? resp.getBody() : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchGoogleUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> resp = restTemplate.exchange(
                GOOGLE_USERINFO_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return resp.getBody() != null ? resp.getBody() : Map.of();
    }

    private String exchangeGithubCode(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code",          code);
        params.add("client_id",     githubClientId);
        params.add("client_secret", githubClientSecret);
        params.add("redirect_uri",  redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                GITHUB_TOKEN_URL, new HttpEntity<>(params, headers), Map.class);

        if (resp.getBody() == null) {
            throw new IllegalStateException("GitHub token exchange returned empty body");
        }
        String token = (String) resp.getBody().get("access_token");
        if (token == null) {
            throw new IllegalStateException("GitHub token exchange failed: " + resp.getBody().get("error_description"));
        }
        return token;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchGithubUser(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        ResponseEntity<Map> resp = restTemplate.exchange(
                GITHUB_USER_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return resp.getBody() != null ? resp.getBody() : Map.of();
    }

    @SuppressWarnings("unchecked")
    private String resolveGithubEmail(Map<String, Object> userProfile, String accessToken) {
        // Use public email if available
        String email = (String) userProfile.get("email");
        if (email != null && !email.isBlank()) return email;

        // Otherwise fetch /user/emails and pick the primary verified one
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        try {
            ResponseEntity<List> resp = restTemplate.exchange(
                    GITHUB_EMAILS_URL, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            if (resp.getBody() == null) return null;

            for (Object entry : resp.getBody()) {
                if (!(entry instanceof Map<?, ?> emailEntry)) continue;
                Object primary  = emailEntry.get("primary");
                Object verified = emailEntry.get("verified");
                Object emailVal = emailEntry.get("email");
                if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified) && emailVal instanceof String s) {
                    return s;
                }
            }
            return null;
        } catch (Exception ex) {
            log.warn("Failed to fetch GitHub emails: {}", ex.getMessage());
            return null;
        }
    }
}
