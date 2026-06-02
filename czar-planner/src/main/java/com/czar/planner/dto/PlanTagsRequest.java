package com.czar.planner.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PlanTagsRequest(
        @NotNull List<UUID> tagIds
) {}
