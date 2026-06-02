package com.czar.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TagCreateRequest(
        @NotBlank(message = "name is required")
        @Size(min = 1, max = 50, message = "name must be between 1 and 50 characters")
        String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "colorHex must be a valid hex colour (e.g. #6366F1)")
        String colorHex
) {}
