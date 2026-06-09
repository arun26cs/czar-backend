-- V2: Add plan_tags junction table
-- This table was added to V1 after the initial migration was applied.
-- Created as a separate V2 migration to avoid checksum conflicts.

CREATE TABLE IF NOT EXISTS planner.plan_tags (
    plan_id  UUID NOT NULL REFERENCES planner.plans(id) ON DELETE CASCADE,
    tag_id   UUID NOT NULL,
    PRIMARY KEY (plan_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_plan_tags_plan_id ON planner.plan_tags (plan_id);
