-- =============================================================================
-- czar-auth  |  auth schema  |  V1 initial migration
-- =============================================================================

-- Users identity table (email / phone, no passwords stored)
CREATE TABLE IF NOT EXISTS auth.users_auth (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email             TEXT        UNIQUE,
    phone             TEXT        UNIQUE,
    email_verified    BOOLEAN     NOT NULL DEFAULT FALSE,
    phone_verified    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at     TIMESTAMPTZ,

    CONSTRAINT users_auth_email_or_phone CHECK (
        email IS NOT NULL OR phone IS NOT NULL
    )
);

-- OTP request log (email or phone identifier, hashed OTP)
CREATE TABLE IF NOT EXISTS auth.otp_requests (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier    TEXT        NOT NULL,           -- email or E.164 phone
    otp_hash      TEXT        NOT NULL,           -- BCrypt hash
    expires_at    TIMESTAMPTZ NOT NULL,
    used          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address    TEXT
);

-- Refresh token store (stored as BCrypt hash, never plaintext)
CREATE TABLE IF NOT EXISTS auth.refresh_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES auth.users_auth(id) ON DELETE CASCADE,
    token_hash   TEXT        NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    device_hint  TEXT
);

-- OAuth2 provider connections
CREATE TABLE IF NOT EXISTS auth.oauth_connections (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES auth.users_auth(id) ON DELETE CASCADE,
    provider           TEXT        NOT NULL,      -- 'google' | 'github'
    provider_user_id   TEXT        NOT NULL,
    provider_email     TEXT,
    access_token       TEXT,                      -- encrypted at app layer before storage
    connected_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT oauth_connections_provider_user_unique UNIQUE (provider, provider_user_id)
);

-- =============================================================================
-- Indexes
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_otp_requests_identifier
    ON auth.otp_requests (identifier);

CREATE INDEX IF NOT EXISTS idx_otp_requests_expires_at
    ON auth.otp_requests (expires_at);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
    ON auth.refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_oauth_connections_user_id
    ON auth.oauth_connections (user_id);
