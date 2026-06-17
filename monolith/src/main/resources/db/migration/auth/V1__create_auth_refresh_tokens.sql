-- Migration: V1__create_auth_refresh_tokens.sql
-- Creates the auth schema and refresh_tokens table per Schema doc §1.1.
--
-- Design decisions:
--   - The auth schema is separate from user_module because refresh tokens
--     are short-lived session state, not user identity. Separating them
--     makes the schema easier to audit: auth.* = live sessions only.
--   - token_hash stores SHA-256(plaintext_token). The plaintext is given
--     to the client exactly once and never persisted. If the DB leaks,
--     hashes cannot be reversed to valid tokens (unlike bcrypt, SHA-256
--     is fast — but refresh tokens are random 256-bit values, making
--     brute force infeasible).
--   - replaced_by_id is a self-referencing FK (DEFERRABLE so the row can
--     reference a row in the same table within the same transaction).
--     This encodes the rotation chain: token A → replaced by B → replaced
--     by C. Replay detection walks this chain forward, revoking every
--     descendant of the replayed token.
--   - ON DELETE CASCADE on user_id: when a user row is hard-deleted (rare
--     admin operation), their session rows go with it cleanly.
--   - revoked_reason is constrained to known values. Adding a new reason
--     requires a forward migration — intentional friction to keep the
--     vocabulary stable.
--
-- Rollback strategy: forward-only. Write a V2 corrective migration if needed.

-- ── Schema ─────────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS auth;

-- ── Table ──────────────────────────────────────────────────────────────────

CREATE TABLE auth.refresh_tokens (

    -- Primary key: UUID v7, application-generated
                                     id                  UUID            NOT NULL,

    -- Owner
                                     user_id             UUID            NOT NULL,

    -- Token storage — SHA-256 hex digest of the plaintext token
                                     token_hash          VARCHAR(255)    NOT NULL,

    -- Device information
                                     device_id           VARCHAR(255)    NOT NULL,
                                     device_label        VARCHAR(255)    NULL,

    -- Network context at issue time (IPv6-capable: max 45 chars)
                                     ip_address          VARCHAR(45)     NULL,

    -- Timestamps
                                     created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                                     last_used_at        TIMESTAMPTZ     NULL,
                                     expires_at          TIMESTAMPTZ     NOT NULL,  -- created_at + 7 days, set by application

    -- Revocation state
                                     revoked_at          TIMESTAMPTZ     NULL,       -- NULL = active
                                     revoked_reason      VARCHAR(50)     NULL,       -- populated when revoked_at is set

    -- Rotation chain: points to the token that replaced this one
                                     replaced_by_id      UUID            NULL,

    -- ── Constraints ──────────────────────────────────────────────────────

                                     CONSTRAINT refresh_tokens_pkey
                                         PRIMARY KEY (id),

                                     CONSTRAINT refresh_tokens_user_fk
                                         FOREIGN KEY (user_id)
                                             REFERENCES user_module.users (id)
                                             ON DELETE CASCADE,

    -- Self-reference for rotation chain tracking.
    -- DEFERRABLE INITIALLY DEFERRED allows INSERT of the new token and
    -- UPDATE of the old token's replaced_by_id to happen in the same
    -- transaction without ordering constraints.
                                     CONSTRAINT refresh_tokens_replaced_by_fk
                                         FOREIGN KEY (replaced_by_id)
                                             REFERENCES auth.refresh_tokens (id)
                                             DEFERRABLE INITIALLY DEFERRED,

                                     CONSTRAINT refresh_tokens_token_hash_unique
                                         UNIQUE (token_hash),

                                     CONSTRAINT refresh_tokens_revoked_reason_check
                                         CHECK (revoked_reason IS NULL OR revoked_reason IN (
                                                                                             'USER_LOGOUT',
                                                                                             'ROTATION',
                                                                                             'ROTATION_REPLAY',
                                                                                             'ADMIN_FORCE',
                                                                                             'EXPIRED'
                                             ))
);

-- ── Indexes ────────────────────────────────────────────────────────────────

-- (user_id, revoked_at) — list a user's sessions ('My Sessions' screen).
-- Active sessions: WHERE revoked_at IS NULL
-- All sessions including revoked: no filter needed, index still useful.
CREATE INDEX refresh_tokens_user_id_revoked_at_idx
    ON auth.refresh_tokens (user_id, revoked_at);

-- Partial index on expires_at for the v0.5 cleanup job.
-- Only covers active (non-revoked) tokens — revoked tokens don't need cleanup.
-- The cleanup job query: WHERE revoked_at IS NULL AND expires_at < NOW()
CREATE INDEX refresh_tokens_expires_at_active_idx
    ON auth.refresh_tokens (expires_at)
    WHERE revoked_at IS NULL;

-- ── Comments ───────────────────────────────────────────────────────────────

COMMENT ON TABLE auth.refresh_tokens IS
    'One row per active or recently-revoked login session. '
    'Tokens rotate on every use (replaced_by_id chain). '
    'Replay detection walks the chain and revokes all descendants. '
    'Schema doc §1.1.';

COMMENT ON COLUMN auth.refresh_tokens.token_hash IS
    'SHA-256 hex digest of the plaintext refresh token. '
    'The plaintext is returned to the client once at login and never stored.';

COMMENT ON COLUMN auth.refresh_tokens.expires_at IS
    'Absolute expiry: created_at + 7 days. Set by the application at issue time. '
    'A token cannot be used after this even if not explicitly revoked.';

COMMENT ON COLUMN auth.refresh_tokens.replaced_by_id IS
    'Points to the successor token after rotation. NULL while still active. '
    'Non-NULL means this token has been rotated; revoked_at will also be set. '
    'Used by replay detection to walk the chain forward.';

COMMENT ON COLUMN auth.refresh_tokens.revoked_reason IS
    'USER_LOGOUT: user explicitly logged out. '
    'ROTATION: normal rotation on successful refresh. '
    'ROTATION_REPLAY: revoked as part of a replay-attack response. '
    'ADMIN_FORCE: admin revoked all sessions for this user. '
    'EXPIRED: revoked by the cleanup job after expires_at passed.';