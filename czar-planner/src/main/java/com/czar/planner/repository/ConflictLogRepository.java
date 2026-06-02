package com.czar.planner.repository;

import com.czar.planner.domain.ConflictLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConflictLogRepository extends JpaRepository<ConflictLog, UUID> {

    List<ConflictLog> findByUserIdAndResolvedAtIsNull(UUID userId);

    boolean existsByPlanAIdAndPlanBIdAndResolvedAtIsNull(UUID planAId, UUID planBId);
}
