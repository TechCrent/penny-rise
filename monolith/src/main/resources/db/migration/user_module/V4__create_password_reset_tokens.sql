-- Migration: V4__create_password_reset_tokens.sql
-- Flyway version 4: after V3 email_verification_tokens (v0.2-004).
-- Creates user_module.password_reset_tokens.
--
-- This table is not explicitly in Schema doc §1 as originally published
-- but is required by the password-reset flow (v0.2-016, v0.2-017).
-- It has been added to Schema doc §1.5 as part of issue v0.2-005.
--
-- Design decisions:
--   - Mirrors email_verification_tokens in structure: same SHA-256 hash
--     storage pattern, same single-use gate via consumed_at. The only
--     differences are: 1-hour expiry (vs 24h for email verification)
--     and the partial index shape, which is optimised for active-token
--     lookup by (user_id, expires_at) rather than the resend pattern.
--   - ON DELETE CASCADE: test teardowns and admin hard-deletes clean up
--     associated reset tokens automatically.
--   - No created_at: derivable from expires_at - 1 hour. Omitted to
--     keep the table minimal. Add in a forward migration if needed.
--   - Partial index on (user_id, expires_at) WHERE consumed_at IS NULL:
--     the reset-password endpoint looks up the most recent active
--     (unconsumed, unexpired) token for a user. This index makes that
--     lookup O(log n) against only the active subset.
--
-- Rollback strategy: forward-only.

CREATE TABLE user_module.password_reset_tokens (

                                                   id              UUID            NOT NULL,
                                                   user_id         UUID            NOT NULL,
                                                   token_hash      VARCHAR(255)    NOT NULL,
                                                   expires_at      TIMESTAMPTZ     NOT NULL,   -- created_at + 1 hour, set by application
                                                   consumed_at     TIMESTAMPTZ     NULL,        -- NULL = unused; non-NULL = consumed

                                                   CONSTRAINT password_reset_tokens_pkey
                                                       PRIMARY KEY (id),

                                                   CONSTRAINT password_reset_tokens_user_fk
                                                       FOREIGN KEY (user_id)
                                                           REFERENCES user_module.users (id)
                                                           ON DELETE CASCADE,

                                                   CONSTRAINT password_reset_tokens_token_hash_unique
                                                       UNIQUE (token_hash)
);

-- Partial index for active-token lookup.
-- Query: WHERE user_id = :uid AND consumed_at IS NULL AND expires_at > NOW()
-- Also supports rate-limit check: how many active tokens does this user have?
CREATE INDEX password_reset_tokens_active_idx
    ON user_module.password_reset_tokens (user_id, expires_at)
    WHERE consumed_at IS NULL;

COMMENT ON TABLE user_module.password_reset_tokens IS
    'One row per dispatched password-reset email. Single-use (consumed_at), '
    '1-hour expiry (expires_at). Added to Schema doc §1.5 in v0.2-005.';

COMMENT ON COLUMN user_module.password_reset_tokens.token_hash IS
    'SHA-256 hex digest of the plaintext token embedded in the reset URL. '
    'Plaintext never stored server-side.';

COMMENT ON COLUMN user_module.password_reset_tokens.expires_at IS
    'Absolute expiry: created_at + 1 hour. Set by the application at issue time.';

COMMENT ON COLUMN user_module.password_reset_tokens.consumed_at IS
    'NULL = token not yet used. Non-NULL = consumed; cannot be used again '
    'even if expires_at has not passed.';