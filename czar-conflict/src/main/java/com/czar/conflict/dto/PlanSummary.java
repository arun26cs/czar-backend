package com.czar.conflict.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Simplified plan view used only for overlap calculation — deserialized from
 * the czar-planner internal endpoint. Extra fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanSummary(
        UUID id,
        UUID userId,
        String title,
        LocalDate scheduledDate,
        int hour,
        int minute,
        int durationMinutes) {

    /** Start time in minutes from midnight. */
    public int startMinutes() {
        return hour * 60 + minute;
    }

    /** End time in minutes from midnight. */
    public int endMinutes() {
        return startMinutes() + durationMinutes;
    }
}
