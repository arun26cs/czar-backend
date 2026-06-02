package com.czar.planner.repository;

import com.czar.planner.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByUserIdAndScheduledDateAndDeletedAtIsNull(UUID userId, LocalDate scheduledDate);

    List<Plan> findByUserIdAndDeletedAtIsNull(UUID userId);

    @Query("SELECT p FROM Plan p WHERE p.userId = :userId AND p.scheduledDate = :date " +
           "AND p.deletedAt IS NULL AND p.status = 'pending'")
    List<Plan> findActiveByUserAndDate(@Param("userId") UUID userId, @Param("date") LocalDate date);
}
