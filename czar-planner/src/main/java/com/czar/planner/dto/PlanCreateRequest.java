package com.czar.planner.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanCreateRequest(
        @NotBlank String title,
        @NotBlank String planType,
        @NotNull LocalDate scheduledDate,
        @Min(0) @Max(23) int hour,
        @Min(0) @Max(59) int minute,
        @Min(1) @Max(1440) int durationMinutes,
        List<UUID> tagIds
) {}
