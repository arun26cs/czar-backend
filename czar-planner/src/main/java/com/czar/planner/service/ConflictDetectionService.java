package com.czar.planner.service;

import com.czar.planner.domain.ConflictLog;
import com.czar.planner.domain.Plan;
import com.czar.planner.repository.ConflictLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * In-memory conflict detection using start/end minutes arithmetic.
 * Two plans conflict when their time windows overlap on the same scheduled_date.
 * Overlap condition: startA < endB AND startB < endA
 */
@Service
public class ConflictDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ConflictDetectionService.class);

    private final ConflictLogRepository conflictLogRepository;

    public ConflictDetectionService(ConflictLogRepository conflictLogRepository) {
        this.conflictLogRepository = conflictLogRepository;
    }

    /**
     * Returns true if plans a and b overlap in time.
     * Does NOT check scheduledDate equality — caller must ensure same date.
     */
    public boolean overlaps(Plan a, Plan b) {
        int startA = a.getHour() * 60 + a.getMinute();
        int endA   = startA + a.getDurationMinutes();
        int startB = b.getHour() * 60 + b.getMinute();
        int endB   = startB + b.getDurationMinutes();
        return startA < endB && startB < endA;
    }

    /**
     * Detects conflicts between {@code plan} and all plans in {@code sameDayPlans},
     * recording new conflicts in the log (idempotent).
     */
    @Transactional
    public void detectAndRecord(Plan plan, List<Plan> sameDayPlans) {
        for (Plan other : sameDayPlans) {
            if (other.getId().equals(plan.getId())) continue;
            if (!overlaps(plan, other)) continue;

            // Canonicalise order so we never duplicate (planA < planB by UUID natural order)
            UUID aId = plan.getId().compareTo(other.getId()) < 0 ? plan.getId() : other.getId();
            UUID bId = plan.getId().compareTo(other.getId()) < 0 ? other.getId() : plan.getId();

            if (conflictLogRepository.existsByPlanAIdAndPlanBIdAndResolvedAtIsNull(aId, bId)) {
                log.debug("Conflict already recorded for plans {} / {} — skipping", aId, bId);
                continue;
            }

            ConflictLog conflict = new ConflictLog();
            conflict.setUserId(plan.getUserId());
            conflict.setPlanAId(aId);
            conflict.setPlanBId(bId);
            conflictLogRepository.save(conflict);
            log.info("Conflict recorded: planA={} planB={} user={}", aId, bId, plan.getUserId());
        }
    }
}
