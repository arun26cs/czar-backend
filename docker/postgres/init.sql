-- =============================================================================
-- Czar Backend — Local PostgreSQL Initialisation
-- Runs automatically when the postgres Docker container first starts.
-- Creates 3 schemas and 3 least-privilege roles mirroring the Neon prod setup.
-- =============================================================================

-- ─── Schemas ─────────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS planner;

-- ─── Roles ───────────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'czar_auth_role') THEN
        CREATE ROLE czar_auth_role WITH LOGIN PASSWORD 'czar_auth_pass';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'czar_user_role') THEN
        CREATE ROLE czar_user_role WITH LOGIN PASSWORD 'czar_user_pass';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'czar_planner_role') THEN
        CREATE ROLE czar_planner_role WITH LOGIN PASSWORD 'czar_planner_pass';
    END IF;
END
$$;

-- ─── czar_auth_role — auth schema only ───────────────────────────────────────
GRANT USAGE  ON SCHEMA auth TO czar_auth_role;
GRANT CREATE ON SCHEMA auth TO czar_auth_role;
GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA auth TO czar_auth_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA auth TO czar_auth_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT ALL ON TABLES    TO czar_auth_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT ALL ON SEQUENCES TO czar_auth_role;

-- ─── czar_user_role — users schema only ──────────────────────────────────────
-- Note: czar-notes service also connects with czar_user_role (notes live in users schema)
GRANT USAGE  ON SCHEMA users TO czar_user_role;
GRANT CREATE ON SCHEMA users TO czar_user_role;
GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA users TO czar_user_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA users TO czar_user_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA users GRANT ALL ON TABLES    TO czar_user_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA users GRANT ALL ON SEQUENCES TO czar_user_role;

-- ─── czar_planner_role — planner schema only ─────────────────────────────────
GRANT USAGE  ON SCHEMA planner TO czar_planner_role;
GRANT CREATE ON SCHEMA planner TO czar_planner_role;
GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA planner TO czar_planner_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA planner TO czar_planner_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA planner GRANT ALL ON TABLES    TO czar_planner_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA planner GRANT ALL ON SEQUENCES TO czar_planner_role;

-- ─── Extensions (required for Phase 2 full-text search & UUID) ───────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;
