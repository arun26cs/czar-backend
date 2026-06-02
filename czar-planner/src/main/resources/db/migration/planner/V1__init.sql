-- =============================================================================
-- czar-planner  |  planner schema  |  V1 initial migration
-- =============================================================================

-- Plans (tasks/events on a timeline)
CREATE TABLE IF NOT EXISTS planner.plans (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL,       -- FK to users.users_profile resolved at app layer
    folder_id         UUID,                       -- FK to users.folders resolved at app layer
    title             TEXT        NOT NULL,
    plan_type         TEXT        NOT NULL,       -- 'task' | 'event' | 'reminder'
    scheduled_date    DATE        NOT NULL,
    hour              SMALLINT    NOT NULL DEFAULT 0  CHECK (hour   BETWEEN 0 AND 23),
    minute            SMALLINT    NOT NULL DEFAULT 0  CHECK (minute BETWEEN 0 AND 59),
    duration_minutes  INT         NOT NULL DEFAULT 30 CHECK (duration_minutes > 0),
    status            TEXT        NOT NULL DEFAULT 'pending', -- 'pending' | 'done' | 'skipped'
    confirmed         BOOLEAN     NOT NULL DEFAULT FALSE,
    ai_generated      BOOLEAN     NOT NULL DEFAULT FALSE,
    reminder_sent     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ                       -- soft delete
);

-- Conflict log — recorded when two plans overlap in time
CREATE TABLE IF NOT EXISTS planner.conflict_log (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    plan_a_id    UUID        NOT NULL REFERENCES planner.plans(id) ON DELETE CASCADE,
    plan_b_id    UUID        NOT NULL REFERENCES planner.plans(id) ON DELETE CASCADE,
    detected_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at  TIMESTAMPTZ
);

-- =============================================================================
-- Indexes
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_plans_user_id
    ON planner.plans (user_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_plans_scheduled_date
    ON planner.plans (scheduled_date)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_plans_user_date
    ON planner.plans (user_id, scheduled_date)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_conflict_log_user_id
    ON planner.conflict_log (user_id);
