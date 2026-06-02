package com.czar.planner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PlanTagId implements Serializable {

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "tag_id")
    private UUID tagId;

    public PlanTagId() {}

    public PlanTagId(UUID planId, UUID tagId) {
        this.planId = planId;
        this.tagId = tagId;
    }

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }

    public UUID getTagId() { return tagId; }
    public void setTagId(UUID tagId) { this.tagId = tagId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanTagId)) return false;
        PlanTagId that = (PlanTagId) o;
        return Objects.equals(planId, that.planId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planId, tagId);
    }
}
