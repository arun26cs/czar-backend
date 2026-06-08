-- czar-user | users schema | V2 — add deleted_at to users_profile for account deletion
-- Required by Apple App Store (account deletion is mandatory for apps with accounts).
-- Soft-delete: sets deleted_at timestamp; hard-delete is scheduled daily for rows
-- where deleted_at < now() - interval '30 days'.

ALTER TABLE users.users_profile
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Index to speed up the daily hard-delete Cloud Scheduler job
CREATE INDEX IF NOT EXISTS idx_users_profile_deleted_at
    ON users.users_profile (deleted_at)
    WHERE deleted_at IS NOT NULL;
