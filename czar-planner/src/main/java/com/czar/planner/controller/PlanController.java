package com.czar.planner.controller;

import com.czar.planner.dto.*;
import com.czar.planner.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> listByDate(
            Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(planService.listByDate(UUID.fromString(auth.getName()), date));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> getById(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(planService.getById(UUID.fromString(auth.getName()), id));
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(
            Authentication auth,
            @Valid @RequestBody PlanCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planService.create(UUID.fromString(auth.getName()), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanResponse> update(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody PlanUpdateRequest request) {
        return ResponseEntity.ok(planService.update(UUID.fromString(auth.getName()), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        planService.delete(UUID.fromString(auth.getName()), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PlanResponse> updateStatus(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody PlanStatusRequest request) {
        return ResponseEntity.ok(planService.updateStatus(UUID.fromString(auth.getName()), id, request));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<PlanResponse> confirm(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(planService.confirm(UUID.fromString(auth.getName()), id));
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<PlanResponse> replaceTags(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody PlanTagsRequest request) {
        return ResponseEntity.ok(planService.replaceTags(UUID.fromString(auth.getName()), id, request));
    }
}
