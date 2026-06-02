package com.czar.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record PreferencesUpdateRequest(
        @Pattern(regexp = "light|dark|system", message = "theme must be light, dark, or system")
        String theme,

        Boolean dashboardCollapsed,

        @Pattern(regexp = "list|grid|calendar", message = "defaultView must be list, grid, or calendar")
        String defaultView,

        @Min(value = 1, message = "reminderMinutes must be at least 1")
        @Max(value = 1440, message = "reminderMinutes must not exceed 1440 (24 hours)")
        Integer reminderMinutes
) {}
