
-- Migration: V3__create_email_verification_tokens.sql
-- Flyway version 3: V1 user_module.users (v0.2-001), V2 auth.refresh_tokens (v0.2-003).
-- Creates user_module.email_verification_tokens per Schema doc §1.3.
--
-- Design decisions:
--   - token_hash stores SHA-256(plaintext_token). The plaintext is
--     embedded in the verification URL emailed to the user and never
--     stored. On click, the server hashes the URL param and does a
--     lookup by hash — same pattern as refresh_tokens.
--   - expires_at is set by the application to created_at + 24 hours.
--     Expired tokens are rejected even if consumed_at is NULL.
--   - consumed_at is the single-use gate: once set, the token cannot
--     be used again even if it has not expired.
--   - ON DELETE CASCADE: if the user row is removed (test teardowns,
--     admin hard-delete), tokens are cleaned up automatically.
--   - No created_at column: the expires_at minus 24h would recover it,
--     but it is not needed by any query; omitting it keeps the table lean.
--     If needed later, add in a forward migration.
--
-- Rollback strategy: forward-only.

CREATE TABLE user_module.email_verification_tokens (

    id              UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    token_hash      VARCHAR(255)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    consumed_at     TIMESTAMPTZ     NULL,

    CONSTRAINT email_verification_tokens_pkey
        PRIMARY KEY (id),

    CONSTRAINT email_verification_tokens_user_fk
        FOREIGN KEY (user_id)
        REFERENCES user_module.users (id)
        ON DELETE CASCADE,

    CONSTRAINT email_verification_tokens_token_hash_unique
        UNIQUE (token_hash)
);

-- (user_id, consumed_at) — resend-rate-limit check:
-- 'does this user already have an unconsumed token?' →
--   WHERE user_id = :uid AND consumed_at IS NULL AND expires_at > NOW()
-- 'how many tokens has this user consumed in the last hour?' →
--   WHERE user_id = :uid AND consumed_at > NOW() - INTERVAL '1 hour'
CREATE INDEX email_verification_tokens_user_id_consumed_at_idx
    ON user_module.email_verification_tokens (user_id, consumed_at);

COMMENT ON TABLE user_module.email_verification_tokens IS
    'One row per dispatched verification email. Single-use (consumed_at), '
    '24-hour expiry (expires_at). Schema doc §1.3.';

COMMENT ON COLUMN user_module.email_verification_tokens.token_hash IS
    'SHA-256 hex digest of the plaintext token embedded in the email URL. '
    'Plaintext never stored server-side.';

COMMENT ON COLUMN user_module.email_verification_tokens.consumed_at IS
    'NULL = token not yet used. Non-NULL = consumed; cannot be used again.';
