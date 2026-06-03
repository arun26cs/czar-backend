package com.czar.planner.controller;

import com.czar.planner.dto.PlanResponse;
import com.czar.planner.service.PlanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Internal service-to-service endpoint for czar-conflict to fetch a user's plans
 * on a given date for overlap checking. Protected by X-Service-Token header.
 */
@RestController
@RequestMapping("/internal/v1/plans")
public class InternalPlanController {

    @Value("${czar.internal.service-token}")
    private String expectedToken;

    private final PlanService planService;

    public InternalPlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> getPlansForDate(
            @RequestHeader("X-Service-Token") String token,
            @RequestParam UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (!expectedToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(planService.listByDate(userId, date));
    }
}
