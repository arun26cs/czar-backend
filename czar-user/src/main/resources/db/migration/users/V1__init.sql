-- =============================================================================
-- czar-user  |  users schema  |  V1 initial migration
-- NOTE: czar-notes shares this schema and reads these tables. Only czar-user
--       owns and runs migrations here.
-- Tag-based design: no folders, no folder_id anywhere.
-- =============================================================================

-- User profile
CREATE TABLE IF NOT EXISTS users.users_profile (
    id            UUID        PRIMARY KEY,        -- same UUID as auth.users_auth.id
    display_name  TEXT        NOT NULL,
    avatar_url    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Per-user preferences
CREATE TABLE IF NOT EXISTS users.preferences (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL UNIQUE REFERENCES users.users_profile(id) ON DELETE CASCADE,
    theme               TEXT        NOT NULL DEFAULT 'system',   -- 'light' | 'dark' | 'system'
    dashboard_collapsed BOOLEAN     NOT NULL DEFAULT FALSE,
    default_view        TEXT        NOT NULL DEFAULT 'list',     -- 'list' | 'grid' | 'calendar'
    reminder_minutes    INT         NOT NULL DEFAULT 15,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Tags for organising notes and plans (replaces folders — flat, no hierarchy)
CREATE TABLE IF NOT EXISTS users.tags (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users.users_profile(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    color_hex   TEXT        NOT NULL DEFAULT '#6366F1',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tags_user_name UNIQUE (user_id, name)
);

-- FCM / APNs device push tokens
CREATE TABLE IF NOT EXISTS users.device_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users.users_profile(id) ON DELETE CASCADE,
    fcm_token   TEXT        NOT NULL UNIQUE,
    platform    TEXT        NOT NULL,             -- 'android' | 'ios'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Notes (rich JSONB body, soft-deleted, full-text search vector — no folder_id)
CREATE TABLE IF NOT EXISTS users.notes (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users.users_profile(id) ON DELETE CASCADE,
    title          TEXT        NOT NULL DEFAULT '',
    body           JSONB       NOT NULL DEFAULT '{}',
    pinned         BOOLEAN     NOT NULL DEFAULT FALSE,
    search_vector  TSVECTOR,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ                       -- soft delete
);

-- Note-tag junction: many notes ↔ many tags
CREATE TABLE IF NOT EXISTS users.note_tags (
    note_id  UUID NOT NULL REFERENCES users.notes(id) ON DELETE CASCADE,
    tag_id   UUID NOT NULL REFERENCES users.tags(id)  ON DELETE CASCADE,
    PRIMARY KEY (note_id, tag_id)
);

-- =============================================================================
-- Full-text search: trigger keeps search_vector in sync with title + body text
-- =============================================================================

CREATE OR REPLACE FUNCTION users.notes_search_vector_update()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.body::text, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notes_search_vector
    BEFORE INSERT OR UPDATE OF title, body
    ON users.notes
    FOR EACH ROW EXECUTE FUNCTION users.notes_search_vector_update();

-- =============================================================================
-- Indexes
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_notes_user_id
    ON users.notes (user_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_notes_search_vector
    ON users.notes USING GIN (search_vector);

CREATE INDEX IF NOT EXISTS idx_tags_user_id
    ON users.tags (user_id);

CREATE INDEX IF NOT EXISTS idx_note_tags_note_id
    ON users.note_tags (note_id);

CREATE INDEX IF NOT EXISTS idx_note_tags_tag_id
    ON users.note_tags (tag_id);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id
    ON users.device_tokens (user_id);
