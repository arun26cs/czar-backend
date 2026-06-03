package com.czar.planner.service;

import com.czar.common.exception.ResourceNotFoundException;
import com.czar.planner.domain.ConflictLog;
import com.czar.planner.domain.Plan;
import com.czar.planner.domain.PlanTag;
import com.czar.planner.domain.PlanTagId;
import com.czar.planner.dto.*;
import com.czar.planner.messaging.PlanEventPublisher;
import com.czar.planner.repository.ConflictLogRepository;
import com.czar.planner.repository.PlanRepository;
import com.czar.planner.repository.PlanTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanTagRepository planTagRepository;
    private final ConflictLogRepository conflictLogRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final PlanEventPublisher eventPublisher;

    public PlanService(
            PlanRepository planRepository,
            PlanTagRepository planTagRepository,
            ConflictLogRepository conflictLogRepository,
            ConflictDetectionService conflictDetectionService,
            PlanEventPublisher eventPublisher) {
        this.planRepository = planRepository;
        this.planTagRepository = planTagRepository;
        this.conflictLogRepository = conflictLogRepository;
        this.conflictDetectionService = conflictDetectionService;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlanResponse> listByDate(UUID userId, LocalDate date) {
        return planRepository.findByUserIdAndScheduledDateAndDeletedAtIsNull(userId, date)
                .stream().map(p -> toResponse(p, getTagIds(p.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getById(UUID userId, UUID id) {
        Plan plan = findOwned(userId, id);
        return toResponse(plan, getTagIds(id));
    }

    @Transactional
    public PlanResponse create(UUID userId, PlanCreateRequest request) {
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setTitle(request.title());
        plan.setPlanType(request.planType());
        plan.setScheduledDate(request.scheduledDate());
        plan.setHour((short) request.hour());
        plan.setMinute((short) request.minute());
        plan.setDurationMinutes(request.durationMinutes());
        plan = planRepository.save(plan);

        List<UUID> tagIds = request.tagIds() != null ? request.tagIds() : Collections.emptyList();
        saveTags(plan.getId(), tagIds);

        List<Plan> sameDayPlans = planRepository.findActiveByUserAndDate(userId, request.scheduledDate());
        conflictDetectionService.detectAndRecord(plan, sameDayPlans);

        eventPublisher.publish("plan.created", userId.toString(), plan.getId().toString(), plan.getScheduledDate());
        return toResponse(plan, tagIds);
    }

    @Transactional
    public PlanResponse update(UUID userId, UUID id, PlanUpdateRequest request) {
        Plan plan = findOwned(userId, id);

        if (request.title() != null) plan.setTitle(request.title());
        if (request.planType() != null) plan.setPlanType(request.planType());
        if (request.scheduledDate() != null) plan.setScheduledDate(request.scheduledDate());
        if (request.hour() != null) plan.setHour(request.hour().shortValue());
        if (request.minute() != null) plan.setMinute(request.minute().shortValue());
        if (request.durationMinutes() != null) plan.setDurationMinutes(request.durationMinutes());
        plan = planRepository.save(plan);

        List<UUID> tagIds;
        if (request.tagIds() != null) {
            planTagRepository.deleteByPlanId(id);
            saveTags(id, request.tagIds());
            tagIds = request.tagIds();
        } else {
            tagIds = getTagIds(id);
        }

        List<Plan> sameDayPlans = planRepository.findActiveByUserAndDate(userId, plan.getScheduledDate());
        conflictDetectionService.detectAndRecord(plan, sameDayPlans);

        eventPublisher.publish("plan.updated", userId.toString(), plan.getId().toString(), plan.getScheduledDate());
        return toResponse(plan, tagIds);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Plan plan = findOwned(userId, id);
        plan.setDeletedAt(Instant.now());
        planRepository.save(plan);
        eventPublisher.publish("plan.deleted", userId.toString(), plan.getId().toString(), plan.getScheduledDate());
    }

    @Transactional
    public PlanResponse updateStatus(UUID userId, UUID id, PlanStatusRequest request) {
        Plan plan = findOwned(userId, id);
        plan.setStatus(request.status());
        plan = planRepository.save(plan);
        return toResponse(plan, getTagIds(id));
    }

    @Transactional
    public PlanResponse confirm(UUID userId, UUID id) {
        Plan plan = findOwned(userId, id);
        plan.setConfirmed(true);
        plan = planRepository.save(plan);
        return toResponse(plan, getTagIds(id));
    }

    @Transactional
    public PlanResponse replaceTags(UUID userId, UUID id, PlanTagsRequest request) {
        findOwned(userId, id);
        planTagRepository.deleteByPlanId(id);
        saveTags(id, request.tagIds());
        Plan plan = planRepository.findById(id).orElseThrow();
        return toResponse(plan, request.tagIds());
    }

    // -------------------------------------------------------------------------
    // Conflicts & stats (used by ConflictController)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConflictPairResponse> getConflicts(UUID userId) {
        List<ConflictLog> logs = conflictLogRepository.findByUserIdAndResolvedAtIsNull(userId);
        return logs.stream().map(c -> {
            Plan planA = planRepository.findById(c.getPlanAId()).orElse(null);
            Plan planB = planRepository.findById(c.getPlanBId()).orElse(null);
            return new ConflictPairResponse(
                    c.getId(),
                    c.getPlanAId(), planA != null ? planA.getTitle() : "Unknown",
                    c.getPlanBId(), planB != null ? planB.getTitle() : "Unknown",
                    c.getDetectedAt()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public PlanStatsResponse getStats(UUID userId, LocalDate date) {
        List<Plan> plans = planRepository.findByUserIdAndScheduledDateAndDeletedAtIsNull(userId, date);
        long total   = plans.size();
        long done    = plans.stream().filter(p -> "done".equals(p.getStatus())).count();
        long pending = plans.stream().filter(p -> "pending".equals(p.getStatus())).count();
        long skipped = plans.stream().filter(p -> "skipped".equals(p.getStatus())).count();
        long conflicts = conflictLogRepository.findByUserIdAndResolvedAtIsNull(userId).size();
        return new PlanStatsResponse(total, done, pending, skipped, conflicts);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Plan findOwned(UUID userId, UUID id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Plan", id.toString()));
        if (!plan.getUserId().equals(userId) || plan.getDeletedAt() != null) {
            throw ResourceNotFoundException.of("Plan", id.toString());
        }
        return plan;
    }

    private void saveTags(UUID planId, List<UUID> tagIds) {
        for (UUID tagId : tagIds) {
            planTagRepository.save(new PlanTag(new PlanTagId(planId, tagId)));
        }
    }

    private List<UUID> getTagIds(UUID planId) {
        return planTagRepository.findByIdPlanId(planId).stream()
                .map(pt -> pt.getId().getTagId())
                .toList();
    }

    private PlanResponse toResponse(Plan p, List<UUID> tagIds) {
        return new PlanResponse(
                p.getId(), p.getUserId(), p.getTitle(), p.getPlanType(),
                p.getScheduledDate(), p.getHour(), p.getMinute(), p.getDurationMinutes(),
                p.getStatus(), p.isConfirmed(), p.isAiGenerated(), p.isReminderSent(),
                tagIds, p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
