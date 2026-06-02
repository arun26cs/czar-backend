package com.czar.planner.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "plan_tags", schema = "planner")
public class PlanTag {

    @EmbeddedId
    private PlanTagId id;

    public PlanTag() {}

    public PlanTag(PlanTagId id) {
        this.id = id;
    }

    public PlanTagId getId() { return id; }
    public void setId(PlanTagId id) { this.id = id; }
}
