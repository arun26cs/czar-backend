package com.czar.planner.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanUpdateRequest(
        String title,
        String planType,
        LocalDate scheduledDate,
        @Min(0) @Max(23) Integer hour,
        @Min(0) @Max(59) Integer minute,
        @Min(1) @Max(1440) Integer durationMinutes,
        List<UUID> tagIds
) {}
