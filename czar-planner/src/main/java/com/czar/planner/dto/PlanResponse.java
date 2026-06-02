package com.czar.planner.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        UUID userId,
        String title,
        String planType,
        LocalDate scheduledDate,
        int hour,
        int minute,
        int durationMinutes,
        String status,
        boolean confirmed,
        boolean aiGenerated,
        boolean reminderSent,
        List<UUID> tagIds,
        Instant createdAt,
        Instant updatedAt
) {}
