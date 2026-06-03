package com.czar.conflict.service;

import com.czar.conflict.dto.PlanSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects scheduling conflicts between plans using an O(n²) overlap check.
 *
 * <p>Two plans overlap when: {@code A.start < B.end AND B.start < A.end}
 * where start/end are expressed in minutes from midnight. Adjacent plans
 * (end == start) do NOT conflict.
 */
@Service
public class ConflictChecker {

    /**
     * Returns all pairs of plans that have an overlapping time window.
     * A plan does not conflict with itself.
     *
     * @param plans all active plans for a user on a given day
     * @return list of conflicting pairs (each inner list has exactly 2 plans)
     */
    public List<List<PlanSummary>> detectConflicts(List<PlanSummary> plans) {
        List<List<PlanSummary>> conflicts = new ArrayList<>();

        for (int i = 0; i < plans.size(); i++) {
            for (int j = i + 1; j < plans.size(); j++) {
                PlanSummary a = plans.get(i);
                PlanSummary b = plans.get(j);
                if (overlaps(a, b)) {
                    conflicts.add(List.of(a, b));
                }
            }
        }
        return conflicts;
    }

    private boolean overlaps(PlanSummary a, PlanSummary b) {
        return a.startMinutes() < b.endMinutes() && b.startMinutes() < a.endMinutes();
    }
}
