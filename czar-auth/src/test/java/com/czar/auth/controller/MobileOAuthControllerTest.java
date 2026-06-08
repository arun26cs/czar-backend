package com.czar.auth.controller;

import com.czar.auth.config.OAuth2SuccessHandler;
import com.czar.auth.dto.MobileOAuthRequest;
import com.czar.auth.dto.TokenPairResponse;
import com.czar.auth.service.MobileOAuthService;
import com.czar.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fix 2 — Mobile OAuth2 controller tests.
 *
 * SecurityConfig for czar-auth uses .anyRequest().permitAll(), so no auth header needed.
 * OAuth2SuccessHandler is mocked to satisfy SecurityConfig's constructor dependency.
 */
@WebMvcTest(MobileOAuthController.class)
@Import({GlobalExceptionHandler.class, com.czar.auth.config.SecurityConfig.class})
class MobileOAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MobileOAuthService mobileOAuthService;

    // Required to satisfy SecurityConfig constructor which injects OAuth2SuccessHandler
    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    private static final String VALID_CODE         = "auth-code-abc123";
    private static final String VALID_REDIRECT_URI = "czar://auth/callback";

    private static final TokenPairResponse DUMMY_TOKENS = new TokenPairResponse(
            "access.token.here", "refresh.token.here", 900, "Bearer");

    // ── POST /auth/oauth/google/mobile ──────────────────────────────────────

    @Test
    void googleMobile_validRequest_returns200WithTokens() throws Exception {
        when(mobileOAuthService.googleMobileLogin(any(MobileOAuthRequest.class), any()))
                .thenReturn(DUMMY_TOKENS);

        mockMvc.perform(post("/auth/oauth/google/mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MobileOAuthRequest(VALID_CODE, VALID_REDIRECT_URI))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token.here"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void googleMobile_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/auth/oauth/google/mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MobileOAuthRequest("", VALID_REDIRECT_URI))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void googleMobile_blankRedirectUri_returns400() throws Exception {
        mockMvc.perform(post("/auth/oauth/google/mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MobileOAuthRequest(VALID_CODE, ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void googleMobile_wrongRedirectUri_returns400() throws Exception {
        when(mobileOAuthService.googleMobileLogin(any(MobileOAuthRequest.class), any()))
                .thenThrow(new IllegalArgumentException("Invalid redirectUri"));

        mockMvc.perform(post("/auth/oauth/google/mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MobileOAuthRequest(VALID_CODE, "https://evil.com/callback"))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /auth/oauth/github/mobile ──────────────────────────────────────

    @Test
    void githubMobile_validRequest_returns200WithTokens() throws Exception {
        when(mobileOAuthService.githubMobileLogin(any(MobileOAuthRequest.class), any()))
                .thenReturn(DUMMY_TOKENS);

        mockMvc.perform(post("/auth/oauth/github/mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MobileOAuthRequest(VALID_CODE, VALID_REDIRECT_URI))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh.token.here"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void githubMobile_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/auth/oauth/github/mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MobileOAuthRequest("", VALID_REDIRECT_URI))))
                .andExpect(status().isBadRequest());
    }
}
