package com.czar.user.controller;

import com.czar.user.domain.DeviceToken;
import com.czar.user.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalUserController.class)
@Import(com.czar.user.config.SecurityConfig.class)
@TestPropertySource(properties = "czar.internal.service-token=test-token")
class InternalUserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean DeviceTokenRepository deviceTokenRepository;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private DeviceToken deviceToken(String fcmToken) {
        DeviceToken dt = new DeviceToken();
        dt.setUserId(USER_ID);
        dt.setFcmToken(fcmToken);
        dt.setPlatform("android");
        return dt;
    }

    @Test
    void validToken_withDeviceToken_returnsToken() throws Exception {
        when(deviceTokenRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(deviceToken("fcm-abc-123")));

        mockMvc.perform(get("/internal/v1/device-tokens")
                        .header("X-Service-Token", "test-token")
                        .param("userId", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fcmToken").value("fcm-abc-123"))
                .andExpect(jsonPath("$.platform").value("android"));
    }

    @Test
    void validToken_noDeviceToken_returns404() throws Exception {
        when(deviceTokenRepository.findByUserId(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/device-tokens")
                        .header("X-Service-Token", "test-token")
                        .param("userId", USER_ID.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidToken_returns403() throws Exception {
        mockMvc.perform(get("/internal/v1/device-tokens")
                        .header("X-Service-Token", "wrong-token")
                        .param("userId", USER_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingToken_returns400() throws Exception {
        mockMvc.perform(get("/internal/v1/device-tokens")
                        .param("userId", USER_ID.toString()))
                .andExpect(status().is4xxClientError());
    }
}
