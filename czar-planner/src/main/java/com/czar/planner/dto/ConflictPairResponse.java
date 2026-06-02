package com.czar.planner.dto;

import java.time.Instant;
import java.util.UUID;

public record ConflictPairResponse(
        UUID conflictId,
        UUID planAId,
        String planATitle,
        UUID planBId,
        String planBTitle,
        Instant detectedAt
) {}
