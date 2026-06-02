package com.czar.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeviceTokenRequest(
        @NotBlank(message = "fcmToken is required")
        String fcmToken,

        @NotBlank(message = "platform is required")
        @Pattern(regexp = "android|ios", message = "platform must be android or ios")
        String platform
) {}
