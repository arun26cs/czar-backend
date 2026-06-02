package com.czar.user.dto;

import java.time.Instant;
import java.util.UUID;

public record PreferencesResponse(
        UUID id,
        UUID userId,
        String theme,
        boolean dashboardCollapsed,
        String defaultView,
        int reminderMinutes,
        Instant updatedAt
) {}
