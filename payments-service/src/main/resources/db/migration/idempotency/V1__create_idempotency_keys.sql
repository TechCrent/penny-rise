-- Migration: V1__create_idempotency_keys.sql
-- Creates the idempotency schema and idempotency.idempotency_keys table
-- per Schema doc §7.1 and System Design §6.1.
--
-- Design decisions:
--   - Column name is key_value (not 'key' — reserved word in Postgres;
--     using it requires quoting everywhere and is a maintenance hazard).
--
--   - state uses three values: PROCESSING, COMPLETED, FAILED.
--     The issue description lists two (PROCESSING, COMPLETED) but the
--     Schema doc §7.1 and System Design §6.1 both include FAILED.
--     A failed operation still holds the idempotency row — the row was
--     inserted in PROCESSING at the start of the request, and must be
--     resolvable even if the operation itself fails. Without FAILED, a
--     failed request leaves a permanently-PROCESSING row that blocks all
--     retries with 409 forever. See tracking flag below.
--
--   - request_hash is SHA-256(method + path + body). Stored as
--     VARCHAR(255) holding the hex-encoded hash (64 characters for
--     SHA-256). The idempotency filter (v0.3-009) computes it and
--     compares; a mismatch on a COMPLETED row returns 422 per
--     System Design §6.1.
--
--   - request_path stored separately from the hash so ops can query
--     "which endpoint owns this key" without needing to decode the hash.
--
--   - response_body stored as JSONB. The Payments Service only produces
--     JSON responses; JSONB lets the cleanup job inspect body contents
--     if needed, and gives better storage efficiency than TEXT.
--
--   - expires_at = created_at + 24 hours (set by the application layer,
--     not a Postgres DEFAULT, because the write service computes it
--     explicitly so there is no ambiguity about the window). The daily
--     cleanup job selects WHERE expires_at < NOW() and deletes in batches.
--
--   - No FK to transaction.transactions — an idempotency key may be
--     created for a request that ultimately fails and produces no
--     transaction row. The link is logical, via the idempotency_key
--     column on transaction.transactions (audit trail only).
--
--   - No UPDATE or DELETE from the application role is revoked here.
--     Unlike ledger_entries, idempotency rows are legitimately updated
--     (PROCESSING → COMPLETED/FAILED with the cached response) and
--     deleted (by the cleanup job). Full DML is appropriate.
--
-- Rollback strategy: forward-only.

CREATE SCHEMA IF NOT EXISTS idempotency;

CREATE TABLE idempotency.idempotency_keys (

    id                      UUID            NOT NULL,

    key_value               VARCHAR(255)    NOT NULL,   -- The Idempotency-Key header value
    request_hash            VARCHAR(255)    NOT NULL,   -- SHA-256(method + path + body); hex-encoded
    request_path            VARCHAR(500)    NOT NULL,   -- Endpoint that owns this key (e.g. POST /api/v1/transactions/deposits)

    state                   VARCHAR(50)     NOT NULL DEFAULT 'PROCESSING',

    response_status_code    INT             NULL,       -- NULL until COMPLETED or FAILED
    response_body           JSONB           NULL,       -- NULL until COMPLETED or FAILED

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMPTZ     NOT NULL,   -- Set by application: created_at + 24h

    CONSTRAINT idempotency_keys_pkey
        PRIMARY KEY (id),

    CONSTRAINT idempotency_keys_key_value_uk
        UNIQUE (key_value),

    CONSTRAINT idempotency_keys_state_check
        CHECK (state IN ('PROCESSING', 'COMPLETED', 'FAILED')),

    CONSTRAINT idempotency_keys_response_on_terminal_check
        CHECK (
            -- Terminal states must have a response cached
            (state IN ('COMPLETED', 'FAILED') AND response_status_code IS NOT NULL)
            OR state = 'PROCESSING'
        ),

    CONSTRAINT idempotency_keys_expires_after_created_check
        CHECK (expires_at > created_at)
);

-- Covered by the unique constraint — key_value lookups use this index.
-- No separate index needed on key_value.

-- Schema doc §7.1: cleanup job index — DELETE WHERE expires_at < NOW()
CREATE INDEX idempotency_keys_expires_at_idx
    ON idempotency.idempotency_keys (expires_at);

-- Ops index: find all keys for a given endpoint
-- ("show me all in-flight PROCESSING keys for /transactions/deposits")
CREATE INDEX idempotency_keys_request_path_state_idx
    ON idempotency.idempotency_keys (request_path, state)
    WHERE state = 'PROCESSING';

COMMENT ON TABLE idempotency.idempotency_keys IS
    'Backing store for the Payments Service idempotency framework. '
    'Every mutating request inserts a row in PROCESSING state; on completion '
    'the row is updated to COMPLETED or FAILED with the cached response. '
    'Retries with the same key receive the cached response. '
    'Schema doc §7.1, System Design §6.1.';

COMMENT ON COLUMN idempotency.idempotency_keys.key_value IS
    'The client-supplied Idempotency-Key header value. One key per logical '
    'user operation — a single tap of Deposit produces one key even if '
    'the HTTP call is retried ten times. UNIQUE enforced at DB level.';

COMMENT ON COLUMN idempotency.idempotency_keys.request_hash IS
    'SHA-256(HTTP method + request path + request body), hex-encoded (64 chars). '
    'If an incoming request matches the key_value but not this hash, the '
    'idempotency filter returns 422 — key reuse for a different operation '
    'is a client bug that must be surfaced, not silently honoured.';

COMMENT ON COLUMN idempotency.idempotency_keys.state IS
    'PROCESSING: request is in flight; concurrent duplicates get 409. '
    'COMPLETED: operation finished (success); retries get the cached response. '
    'FAILED: operation finished (failure); retries get the cached error response. '
    'A row never stays in PROCESSING permanently — the idempotency filter '
    'updates state on completion regardless of outcome.';

COMMENT ON COLUMN idempotency.idempotency_keys.expires_at IS
    'created_at + 24 hours, set explicitly by the application. '
    'The daily cleanup job deletes rows WHERE expires_at < NOW().';
