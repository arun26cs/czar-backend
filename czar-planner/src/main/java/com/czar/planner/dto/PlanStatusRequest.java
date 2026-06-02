package com.czar.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PlanStatusRequest(
        @NotBlank
        @Pattern(regexp = "pending|done|skipped", message = "status must be pending, done, or skipped")
        String status
) {}
