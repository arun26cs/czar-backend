package com.czar.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String displayName,
        String avatarUrl,
        Instant createdAt,
        Instant updatedAt
) {}
