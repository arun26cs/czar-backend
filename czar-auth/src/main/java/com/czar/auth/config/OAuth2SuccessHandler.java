package com.czar.auth.config;

import com.czar.auth.controller.OAuthController;
import com.czar.auth.dto.TokenPairResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Invoked by Spring Security after a successful OAuth2 login.
 * Writes the JWT token pair as JSON directly into the HTTP response.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthController oauthController;
    private final ObjectMapper objectMapper;

    public OAuth2SuccessHandler(OAuthController oauthController, ObjectMapper objectMapper) {
        this.oauthController = oauthController;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId(); // "google" or "github"

        TokenPairResponse tokenPair = switch (registrationId) {
            case "google" -> oauthController.handleGoogleSuccess(token, request);
            case "github" -> oauthController.handleGithubSuccess(token, request);
            default -> throw new IllegalArgumentException("Unknown OAuth2 provider: " + registrationId);
        };

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), tokenPair);
    }
}
