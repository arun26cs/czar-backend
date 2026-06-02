package com.czar.user.dto;

import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @Size(min = 1, max = 100, message = "displayName must be between 1 and 100 characters")
        String displayName,

        @Size(max = 2048, message = "avatarUrl must not exceed 2048 characters")
        String avatarUrl
) {}
