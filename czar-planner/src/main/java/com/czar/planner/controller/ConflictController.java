package com.czar.planner.controller;

import com.czar.planner.dto.ConflictPairResponse;
import com.czar.planner.dto.PlanStatsResponse;
import com.czar.planner.service.PlanService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
public class ConflictController {

    private final PlanService planService;

    public ConflictController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/conflicts")
    public ResponseEntity<List<ConflictPairResponse>> getConflicts(Authentication auth) {
        return ResponseEntity.ok(planService.getConflicts(UUID.fromString(auth.getName())));
    }

    @GetMapping("/stats")
    public ResponseEntity<PlanStatsResponse> getStats(
            Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(planService.getStats(UUID.fromString(auth.getName()), date));
    }
}
