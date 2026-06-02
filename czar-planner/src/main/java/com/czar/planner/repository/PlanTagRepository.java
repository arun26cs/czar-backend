package com.czar.planner.repository;

import com.czar.planner.domain.PlanTag;
import com.czar.planner.domain.PlanTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlanTagRepository extends JpaRepository<PlanTag, PlanTagId> {

    List<PlanTag> findByIdPlanId(UUID planId);

    @Modifying
    @Query("DELETE FROM PlanTag pt WHERE pt.id.planId = :planId")
    void deleteByPlanId(@Param("planId") UUID planId);
}
