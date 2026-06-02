package com.czar.planner.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conflict_log", schema = "planner")
public class ConflictLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_a_id", nullable = false)
    private UUID planAId;

    @Column(name = "plan_b_id", nullable = false)
    private UUID planBId;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    protected void onCreate() {
        this.detectedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getPlanAId() { return planAId; }
    public void setPlanAId(UUID planAId) { this.planAId = planAId; }

    public UUID getPlanBId() { return planBId; }
    public void setPlanBId(UUID planBId) { this.planBId = planBId; }

    public Instant getDetectedAt() { return detectedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
