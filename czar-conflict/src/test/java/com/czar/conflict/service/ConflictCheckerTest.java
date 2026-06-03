package com.czar.conflict.service;

import com.czar.conflict.dto.PlanSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictCheckerTest {

    private final ConflictChecker checker = new ConflictChecker();
    private final LocalDate DATE = LocalDate.of(2025, 6, 1);
    private final UUID USER = UUID.randomUUID();

    private PlanSummary plan(String title, int hour, int minute, int durationMinutes) {
        return new PlanSummary(UUID.randomUUID(), USER, title, DATE, hour, minute, durationMinutes);
    }

    @Test
    void emptyList_noConflicts() {
        assertThat(checker.detectConflicts(List.of())).isEmpty();
    }

    @Test
    void singlePlan_noConflicts() {
        assertThat(checker.detectConflicts(List.of(plan("Run", 7, 0, 60)))).isEmpty();
    }

    @Test
    void twoNonOverlappingPlans_noConflicts() {
        // 7:00-8:00 and 9:00-10:00
        var plans = List.of(
                plan("Run",    7, 0, 60),
                plan("Standup", 9, 0, 60)
        );
        assertThat(checker.detectConflicts(plans)).isEmpty();
    }

    @Test
    void adjacentPlans_noConflict() {
        // 7:00-8:00 and 8:00-9:00 — end == start is NOT an overlap
        var plans = List.of(
                plan("Run",     7, 0, 60),
                plan("Standup", 8, 0, 60)
        );
        assertThat(checker.detectConflicts(plans)).isEmpty();
    }

    @Test
    void twoOverlappingPlans_detectsOnePair() {
        // 7:00-8:30 and 8:00-9:00 — overlap: 8:00-8:30
        var plans = List.of(
                plan("Run",     7, 0, 90),
                plan("Standup", 8, 0, 60)
        );
        List<List<PlanSummary>> result = checker.detectConflicts(plans);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).extracting(PlanSummary::title)
                .containsExactlyInAnyOrder("Run", "Standup");
    }

    @Test
    void containedPlan_detectsConflict() {
        // 7:00-10:00 contains 8:00-9:00
        var plans = List.of(
                plan("Long Meeting", 7, 0, 180),
                plan("Short Task",   8, 0,  60)
        );
        assertThat(checker.detectConflicts(plans)).hasSize(1);
    }

    @Test
    void threePlansAllOverlapping_detectsThreePairs() {
        // 7:00-9:00, 7:30-9:30, 8:00-10:00 — all three overlap with each other
        var plans = List.of(
                plan("A", 7,  0, 120),
                plan("B", 7, 30, 120),
                plan("C", 8,  0, 120)
        );
        assertThat(checker.detectConflicts(plans)).hasSize(3);
    }

    @Test
    void twoPlansOneConflicting_detectsCorrectPair() {
        // Plan A: 7:00-8:00, Plan B: 9:00-10:00, Plan C: 7:30-8:30
        // A-C conflict; A-B no conflict; B-C no conflict
        var a = plan("A", 7,  0, 60);
        var b = plan("B", 9,  0, 60);
        var c = plan("C", 7, 30, 60);
        List<List<PlanSummary>> result = checker.detectConflicts(List.of(a, b, c));
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).extracting(PlanSummary::title)
                .containsExactlyInAnyOrder("A", "C");
    }
}
