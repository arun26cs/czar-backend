package com.czar.conflict.service;

import com.czar.conflict.dto.PlanSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Calls czar-planner's internal endpoint to fetch plans for conflict checking.
 * Failures are handled gracefully — returns empty list so the subscriber can continue.
 */
@Service
public class PlannerClientImpl implements PlannerClient {

    private static final Logger log = LoggerFactory.getLogger(PlannerClientImpl.class);

    private final RestClient restClient;

    public PlannerClientImpl(RestClient plannerRestClient) {
        this.restClient = plannerRestClient;
    }

    @Override
    public List<PlanSummary> getPlansForDate(UUID userId, LocalDate date) {
        try {
            List<PlanSummary> plans = restClient.get()
                    .uri("/internal/v1/plans?userId={userId}&date={date}", userId, date)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return plans != null ? plans : Collections.emptyList();
        } catch (RestClientException ex) {
            log.warn("Failed to fetch plans from czar-planner for userId={} date={}: {}",
                    userId, date, ex.getMessage());
            return Collections.emptyList();
        }
    }
}
