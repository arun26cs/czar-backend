package com.czar.planner.service;

import com.czar.planner.domain.ConflictLog;
import com.czar.planner.domain.Plan;
import com.czar.planner.repository.ConflictLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConflictDetectionServiceTest {

    @Mock
    ConflictLogRepository conflictLogRepository;

    @InjectMocks
    ConflictDetectionService conflictDetectionService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // overlaps()
    // -------------------------------------------------------------------------

    @Test
    void overlaps_returnsTrue_whenPlansFullyOverlap() {
        Plan a = plan(8, 0, 60);  // 08:00–09:00
        Plan b = plan(8, 30, 60); // 08:30–09:30
        assertThat(conflictDetectionService.overlaps(a, b)).isTrue();
    }

    @Test
    void overlaps_returnsTrue_whenPlanBInsideA() {
        Plan a = plan(8, 0, 120); // 08:00–10:00
        Plan b = plan(8, 30, 30); // 08:30–09:00
        assertThat(conflictDetectionService.overlaps(a, b)).isTrue();
    }

    @Test
    void overlaps_returnsFalse_whenAEndsBeforeBStarts() {
        Plan a = plan(8, 0, 60);  // 08:00–09:00
        Plan b = plan(9, 0, 60);  // 09:00–10:00 — adjacent, NOT overlapping
        assertThat(conflictDetectionService.overlaps(a, b)).isFalse();
    }

    @Test
    void overlaps_returnsFalse_whenBEndsBeforeAStarts() {
        Plan a = plan(10, 0, 60); // 10:00–11:00
        Plan b = plan(8, 0, 60);  // 08:00–09:00
        assertThat(conflictDetectionService.overlaps(a, b)).isFalse();
    }

    @Test
    void overlaps_returnsTrue_whenAStartsInsideB() {
        Plan a = plan(9, 30, 60);  // 09:30–10:30
        Plan b = plan(9, 0, 120);  // 09:00–11:00
        assertThat(conflictDetectionService.overlaps(a, b)).isTrue();
    }

    // -------------------------------------------------------------------------
    // detectAndRecord()
    // -------------------------------------------------------------------------

    @Test
    void detectAndRecord_savesConflict_whenOverlapDetected() {
        Plan plan = planWithId(userId, 8, 0, 60);
        Plan other = planWithId(userId, 8, 30, 60);

        when(conflictLogRepository.existsByPlanAIdAndPlanBIdAndResolvedAtIsNull(any(), any()))
                .thenReturn(false);

        conflictDetectionService.detectAndRecord(plan, List.of(plan, other));

        verify(conflictLogRepository).save(any(ConflictLog.class));
    }

    @Test
    void detectAndRecord_skipsSelf() {
        Plan plan = planWithId(userId, 8, 0, 60);

        conflictDetectionService.detectAndRecord(plan, List.of(plan));

        verify(conflictLogRepository, never()).save(any());
    }

    @Test
    void detectAndRecord_skipsExistingConflict() {
        Plan plan  = planWithId(userId, 8, 0, 60);
        Plan other = planWithId(userId, 8, 30, 60);

        when(conflictLogRepository.existsByPlanAIdAndPlanBIdAndResolvedAtIsNull(any(), any()))
                .thenReturn(true);

        conflictDetectionService.detectAndRecord(plan, List.of(plan, other));

        verify(conflictLogRepository, never()).save(any());
    }

    @Test
    void detectAndRecord_noConflict_whenNoOverlap() {
        Plan plan  = planWithId(userId, 8, 0, 60);  // 08:00–09:00
        Plan other = planWithId(userId, 9, 0, 60);  // 09:00–10:00

        conflictDetectionService.detectAndRecord(plan, List.of(plan, other));

        verify(conflictLogRepository, never()).save(any());
    }

    @Test
    void detectAndRecord_canonicalisesOrder() {
        // Ensure plan with smaller UUID is always planA
        Plan plan  = planWithId(userId, 8, 0, 60);
        Plan other = planWithId(userId, 8, 30, 60);

        when(conflictLogRepository.existsByPlanAIdAndPlanBIdAndResolvedAtIsNull(any(), any()))
                .thenReturn(false);

        conflictDetectionService.detectAndRecord(plan, List.of(plan, other));

        verify(conflictLogRepository).save(argThat(c -> {
            UUID a = c.getPlanAId();
            UUID b = c.getPlanBId();
            return a.compareTo(b) < 0; // planA must be < planB
        }));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private Plan plan(int hour, int minute, int duration) {
        Plan p = new Plan();
        p.setHour((short) hour);
        p.setMinute((short) minute);
        p.setDurationMinutes(duration);
        return p;
    }

    private Plan planWithId(UUID userId, int hour, int minute, int duration) {
        Plan p = plan(hour, minute, duration);
        p.setUserId(userId);
        p.setTitle("Test");
        p.setPlanType("task");
        p.setScheduledDate(LocalDate.of(2025, 6, 1));
        // Assign a random UUID via reflection workaround — use a helper entity subclass
        try {
            var field = Plan.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(p, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return p;
    }
}
