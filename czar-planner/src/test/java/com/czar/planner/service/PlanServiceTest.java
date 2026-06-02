package com.czar.planner.service;

import com.czar.common.exception.ResourceNotFoundException;
import com.czar.planner.domain.Plan;
import com.czar.planner.domain.PlanTag;
import com.czar.planner.domain.PlanTagId;
import com.czar.planner.dto.*;
import com.czar.planner.messaging.PlanEventPublisher;
import com.czar.planner.repository.ConflictLogRepository;
import com.czar.planner.repository.PlanRepository;
import com.czar.planner.repository.PlanTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock PlanRepository planRepository;
    @Mock PlanTagRepository planTagRepository;
    @Mock ConflictLogRepository conflictLogRepository;
    @Mock ConflictDetectionService conflictDetectionService;
    @Mock PlanEventPublisher eventPublisher;

    @InjectMocks
    PlanService planService;

    private UUID userId;
    private UUID planId;
    private Plan samplePlan;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        planId = UUID.randomUUID();

        samplePlan = new Plan();
        samplePlan.setId(planId);
        samplePlan.setUserId(userId);
        samplePlan.setTitle("Morning Run");
        samplePlan.setPlanType("task");
        samplePlan.setScheduledDate(LocalDate.of(2025, 6, 1));
        samplePlan.setHour((short) 7);
        samplePlan.setMinute((short) 0);
        samplePlan.setDurationMinutes(60);
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void create_savesAndPublishesEvent() {
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(planRepository.findActiveByUserAndDate(any(), any())).thenReturn(Collections.emptyList());

        PlanCreateRequest req = new PlanCreateRequest(
                "Morning Run", "task", LocalDate.of(2025, 6, 1), 7, 0, 60, Collections.emptyList());

        PlanResponse resp = planService.create(userId, req);

        assertThat(resp.title()).isEqualTo("Morning Run");
        assertThat(resp.planType()).isEqualTo("task");
        verify(planRepository).save(any(Plan.class));
        verify(eventPublisher).publish(eq("plan.created"), eq(userId.toString()), any());
    }

    @Test
    void create_withTags_savesTags() {
        UUID tagId = UUID.randomUUID();
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(planRepository.findActiveByUserAndDate(any(), any())).thenReturn(Collections.emptyList());

        PlanCreateRequest req = new PlanCreateRequest(
                "Run", "task", LocalDate.of(2025, 6, 1), 7, 0, 30, List.of(tagId));

        planService.create(userId, req);

        verify(planTagRepository).save(any(PlanTag.class));
    }

    @Test
    void create_runsConflictDetection() {
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(planRepository.findActiveByUserAndDate(any(), any())).thenReturn(List.of(samplePlan));

        PlanCreateRequest req = new PlanCreateRequest(
                "Breakfast", "task", LocalDate.of(2025, 6, 1), 7, 30, 30, null);

        planService.create(userId, req);

        verify(conflictDetectionService).detectAndRecord(any(Plan.class), anyList());
    }

    // -------------------------------------------------------------------------
    // getById
    // -------------------------------------------------------------------------

    @Test
    void getById_returnsResponse() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(samplePlan));
        when(planTagRepository.findByIdPlanId(planId)).thenReturn(Collections.emptyList());

        PlanResponse resp = planService.getById(userId, planId);

        assertThat(resp.title()).isEqualTo("Morning Run");
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> planService.getById(userId, planId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_throwsNotFound_whenWrongOwner() {
        Plan other = new Plan();
        other.setUserId(UUID.randomUUID()); // different owner
        other.setTitle("Other");
        other.setScheduledDate(LocalDate.now());

        when(planRepository.findById(planId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> planService.getById(userId, planId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_changesTitle() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(samplePlan));
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planTagRepository.findByIdPlanId(any())).thenReturn(Collections.emptyList());
        when(planRepository.findActiveByUserAndDate(any(), any())).thenReturn(Collections.emptyList());

        PlanUpdateRequest req = new PlanUpdateRequest("Evening Run", null, null, null, null, null, null);
        PlanResponse resp = planService.update(userId, planId, req);

        assertThat(resp.title()).isEqualTo("Evening Run");
        verify(eventPublisher).publish(eq("plan.updated"), any(), any());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_softDeletes() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(samplePlan));
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        planService.delete(userId, planId);

        assertThat(samplePlan.getDeletedAt()).isNotNull();
        verify(eventPublisher).publish(eq("plan.deleted"), any(), any());
    }

    // -------------------------------------------------------------------------
    // updateStatus
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_changesToDone() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(samplePlan));
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planTagRepository.findByIdPlanId(any())).thenReturn(Collections.emptyList());

        PlanResponse resp = planService.updateStatus(userId, planId, new PlanStatusRequest("done"));

        assertThat(resp.status()).isEqualTo("done");
    }

    // -------------------------------------------------------------------------
    // confirm
    // -------------------------------------------------------------------------

    @Test
    void confirm_setsConfirmedTrue() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(samplePlan));
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planTagRepository.findByIdPlanId(any())).thenReturn(Collections.emptyList());

        PlanResponse resp = planService.confirm(userId, planId);

        assertThat(resp.confirmed()).isTrue();
    }

    // -------------------------------------------------------------------------
    // replaceTags
    // -------------------------------------------------------------------------

    @Test
    void replaceTags_deletesOldAndSavesNew() {
        UUID tagId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.of(samplePlan));

        planService.replaceTags(userId, planId, new PlanTagsRequest(List.of(tagId)));

        verify(planTagRepository).deleteByPlanId(planId);
        verify(planTagRepository).save(any(PlanTag.class));
    }

    // -------------------------------------------------------------------------
    // getStats
    // -------------------------------------------------------------------------

    @Test
    void getStats_countsCorrectly() {
        Plan done = new Plan(); done.setUserId(userId); done.setStatus("done");
        done.setTitle("A"); done.setPlanType("task"); done.setScheduledDate(LocalDate.now());
        Plan pending = new Plan(); pending.setUserId(userId); pending.setStatus("pending");
        pending.setTitle("B"); pending.setPlanType("task"); pending.setScheduledDate(LocalDate.now());

        when(planRepository.findByUserIdAndScheduledDateAndDeletedAtIsNull(eq(userId), any()))
                .thenReturn(List.of(done, pending));
        when(conflictLogRepository.findByUserIdAndResolvedAtIsNull(userId))
                .thenReturn(Collections.emptyList());

        PlanStatsResponse stats = planService.getStats(userId, LocalDate.now());

        assertThat(stats.total()).isEqualTo(2);
        assertThat(stats.done()).isEqualTo(1);
        assertThat(stats.pending()).isEqualTo(1);
        assertThat(stats.skipped()).isZero();
        assertThat(stats.unresolvedConflicts()).isZero();
    }
}
