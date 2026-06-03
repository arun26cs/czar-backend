package com.czar.conflict.service;

import com.czar.conflict.dto.PlanSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fetches plans from czar-planner via the internal service-to-service endpoint.
 */
public interface PlannerClient {

    /**
     * Returns all active (non-deleted) plans for the given user on the given date.
     * Returns an empty list if the planner is unreachable or returns an error.
     */
    List<PlanSummary> getPlansForDate(UUID userId, LocalDate date);
}
