-- =============================================================================
-- czar-notes  |  Test-only Flyway migration
-- Recreates the shared `users` schema tables that czar-notes reads/writes.
-- In production, czar-user owns and runs these migrations.
-- This file exists only on the test classpath so Testcontainers integration
-- tests have the correct schema without depending on czar-user being deployed.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS users;

-- User profile (required by notes FK)
CREATE TABLE IF NOT EXISTS users.users_profile (
    id            UUID        PRIMARY KEY,
    display_name  TEXT        NOT NULL,
    avatar_url    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Tags (required by note_tags FK)
CREATE TABLE IF NOT EXISTS users.tags (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users.users_profile(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    color_hex   TEXT        NOT NULL DEFAULT '#6366F1',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tags_user_name UNIQUE (user_id, name)
);

-- Notes (rich JSONB body, soft-deleted, full-text search vector)
CREATE TABLE IF NOT EXISTS users.notes (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users.users_profile(id) ON DELETE CASCADE,
    title          TEXT        NOT NULL DEFAULT '',
    body           JSONB       NOT NULL DEFAULT '{}',
    pinned         BOOLEAN     NOT NULL DEFAULT FALSE,
    search_vector  TSVECTOR,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ
);

-- Note-tag junction
CREATE TABLE IF NOT EXISTS users.note_tags (
    note_id  UUID NOT NULL REFERENCES users.notes(id) ON DELETE CASCADE,
    tag_id   UUID NOT NULL REFERENCES users.tags(id)  ON DELETE CASCADE,
    PRIMARY KEY (note_id, tag_id)
);

-- Full-text search trigger (keeps search_vector in sync with title + body)
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

-- Indexes
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
