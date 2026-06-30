-- ============================================================
-- v0.5-003 : admin.admin_refresh_tokens and admin.admin_login_attempts
--
-- Mirrors the auth.refresh_tokens hash-and-rotate design (System
-- Design §7.1) but as a SEPARATE table in the admin schema, per the
-- "completely separate, different tables" requirement in this issue
-- and System Design §7.2. Adds ip_address (IP binding, admin-only
-- requirement) and shortens expiry semantics (8h inactivity vs 7
-- days) — these structural differences are why a shared table was
-- never appropriate, not just a naming preference.
--
-- admin_login_attempts is new — customer auth has no equivalent table
-- (System Design doesn't describe lockout for customer login). This
-- table backs the 5-failures/15-minutes escalating lockout.
-- ============================================================

CREATE TABLE admin.admin_refresh_tokens (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    admin_account_id  UUID          NOT NULL,
    token_hash        VARCHAR(64)   NOT NULL,   -- SHA-256 hex digest, 64 chars
    ip_address        VARCHAR(45)   NOT NULL,   -- bound at issuance; IPv6-sized
    issued_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_used_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ   NOT NULL,   -- recomputed on each use: last_used_at + 8h
    revoked_at        TIMESTAMPTZ   NULL,
    revoked_reason    VARCHAR(50)   NULL,       -- ROTATION, ROTATION_REPLAY, IP_MISMATCH, LOGOUT, DEACTIVATED
    replaced_by_id    UUID          NULL,

    CONSTRAINT admin_refresh_tokens_pk
        PRIMARY KEY (id),

    CONSTRAINT admin_refresh_tokens_admin_fk
        FOREIGN KEY (admin_account_id)
        REFERENCES admin.admin_accounts(id)
        ON DELETE CASCADE,

    CONSTRAINT admin_refresh_tokens_replaced_by_fk
        FOREIGN KEY (replaced_by_id)
        REFERENCES admin.admin_refresh_tokens(id)
        ON DELETE SET NULL,

    CONSTRAINT admin_refresh_tokens_token_hash_uk
        UNIQUE (token_hash),

    CONSTRAINT admin_refresh_tokens_revoked_reason_check
        CHECK (revoked_reason IS NULL OR revoked_reason IN
            ('ROTATION', 'ROTATION_REPLAY', 'IP_MISMATCH', 'LOGOUT', 'DEACTIVATED')),

    CONSTRAINT admin_refresh_tokens_revoked_consistency
        CHECK (
            (revoked_at IS NULL AND revoked_reason IS NULL)
            OR
            (revoked_at IS NOT NULL AND revoked_reason IS NOT NULL)
        )
);

COMMENT ON TABLE admin.admin_refresh_tokens IS
    'Opaque refresh tokens for admin sessions. SHA-256 hashed before '
    'storage — the server only ever sees the hash after issuance, same '
    'as auth.refresh_tokens, but completely separate table per the '
    '"admin auth is entirely separate from user auth" requirement. '
    'IP-bound: a refresh from a different IP than ip_address is rejected '
    'with ADMIN_IP_MISMATCH and the token is revoked, forcing full re-login.';

COMMENT ON COLUMN admin.admin_refresh_tokens.expires_at IS
    '8-hour INACTIVITY window, not a fixed expiry — recomputed to '
    'now() + 8h on every successful refresh (last_used_at also bumped). '
    'A token idle for 8h+ is treated as expired even if originally '
    'issued more recently than 8h ago.';
COMMENT ON COLUMN admin.admin_refresh_tokens.ip_address IS
    'Source IP at issuance. Every refresh attempt compares the request''s '
    'current IP against this value; mismatch -> 401 ADMIN_IP_MISMATCH '
    'and the token is revoked with reason IP_MISMATCH.';

CREATE INDEX admin_refresh_tokens_admin_idx
    ON admin.admin_refresh_tokens (admin_account_id, revoked_at);

COMMENT ON INDEX admin.admin_refresh_tokens_admin_idx IS
    'Supports "all active sessions for this admin" — used by '
    'force-logout-style revocation when an admin is deactivated.';

CREATE UNIQUE INDEX admin_refresh_tokens_hash_lookup_idx
    ON admin.admin_refresh_tokens (token_hash)
    WHERE revoked_at IS NULL;

COMMENT ON INDEX admin.admin_refresh_tokens_hash_lookup_idx IS
    'Partial unique index covering the hot path: looking up an '
    'unrevoked token by hash on every refresh call.';

-- ── Lockout tracking ─────────────────────────────────────────────────────

CREATE TABLE admin.admin_login_attempts (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    email        VARCHAR(320) NOT NULL,   -- lowercased; tracked even for unknown emails
                                          -- to prevent email-enumeration via lockout timing
    succeeded    BOOLEAN      NOT NULL,
    ip_address   VARCHAR(45)  NULL,
    attempted_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT admin_login_attempts_pk
        PRIMARY KEY (id)
);

COMMENT ON TABLE admin.admin_login_attempts IS
    'Every admin login attempt, successful or failed, keyed by the '
    'submitted email (not admin_account_id, since a failed attempt for '
    'a non-existent email must still be tracked to prevent timing-based '
    'account enumeration and to support lockout for that email regardless '
    'of whether it resolves to a real account).';

CREATE INDEX admin_login_attempts_email_time_idx
    ON admin.admin_login_attempts (email, attempted_at DESC);

COMMENT ON INDEX admin.admin_login_attempts_email_time_idx IS
    'Supports the lockout check: count failed attempts for this email '
    'within the last 15 minutes, and find the most recent lockout window '
    'to compute escalating duration.';
