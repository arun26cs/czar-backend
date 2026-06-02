package com.czar.user.dto;

import java.time.Instant;
import java.util.UUID;

public record TagResponse(
        UUID id,
        String name,
        String colorHex,
        long noteCount,
        Instant createdAt
) {}
