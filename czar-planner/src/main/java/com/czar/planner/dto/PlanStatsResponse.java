package com.czar.planner.dto;

public record PlanStatsResponse(
        long total,
        long done,
        long pending,
        long skipped,
        long unresolvedConflicts
) {}
