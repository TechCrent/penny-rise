-- Migration: V1__create_outbox_events.sql
-- Creates the outbox schema and outbox.outbox_events table per
-- Schema doc §7.2. Enforces column-level append-only semantics:
-- the application role may INSERT and SELECT freely, but UPDATE is
-- restricted to the four relay-tracking columns only. DELETE is
-- revoked entirely — the relay moves FAILED rows to a dead-letter
-- table (v0.3-010), it does not delete them from this table.
--
-- Design decisions:
--   - status values: PENDING/SENT/FAILED per Schema doc §7.2 and
--     Folder Structure doc OutboxEventStatus. The issue description
--     uses 'DEAD' for exhausted-retry rows — that is the dead-letter
--     TABLE concept, not a status value on this table.
--
--   - Column-level GRANT mechanics: PostgreSQL cannot selectively REVOKE
--     a column from a table-level UPDATE grant. The correct approach is:
--     REVOKE table-level UPDATE entirely, then GRANT UPDATE on only the
--     four relay-tracking columns (status, attempts, sent_at,
--     last_attempted_at). The startup check must verify column-level
--     grants in information_schema.role_column_grants, not the table-level
--     role_table_grants.
--
--   - attempts and last_attempted_at: not in Schema doc §7.2 column table
--     but required by the relay worker and included in the column-level
--     UPDATE GRANT. Tracked additions — same pattern as ledger_transactions
--     status column (v0.3-002). Schema doc §7.2 should be updated to
--     include them.
--
--   - routing_key, schema_version, correlation_id, aggregate_id,
--     aggregate_type: all from Schema doc §7.2. The issue description
--     omits them but they're needed by the relay worker for correct
--     RabbitMQ routing and by consumers for idempotent deduplication.
--
--   - No FK on aggregate_id — the aggregate may live in any schema or
--     service (vault, susu, transfer). Logical reference only.
--
-- Rollback strategy: forward-only. The REVOKE/GRANT cannot be
-- undone without a superuser — by design.

CREATE SCHEMA IF NOT EXISTS outbox;

CREATE TABLE outbox.outbox_events (

    id                  UUID            NOT NULL,

    -- Event identity
    event_type          VARCHAR(100)    NOT NULL,   -- e.g. payments.deposit.completed
    schema_version      VARCHAR(20)     NOT NULL DEFAULT '1.0',

    -- Business context (logical references — no FK possible across schemas/services)
    aggregate_type      VARCHAR(100)    NOT NULL,   -- e.g. VAULT_DEPOSIT, PEER_TRANSFER
    aggregate_id        UUID            NOT NULL,   -- ID of the business object

    -- Content — append-only; never updated after INSERT
    payload             JSONB           NOT NULL,
    routing_key         VARCHAR(255)    NOT NULL,   -- RabbitMQ routing key
    correlation_id      VARCHAR(255)    NULL,

    -- Relay tracking — only these four columns may be UPDATEd
    status              VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    attempts            INT             NOT NULL DEFAULT 0,
    sent_at             TIMESTAMPTZ     NULL,       -- NULL until SENT
    last_attempted_at   TIMESTAMPTZ     NULL,       -- NULL until first attempt

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT outbox_events_pkey
        PRIMARY KEY (id),

    CONSTRAINT outbox_events_status_check
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),

    CONSTRAINT outbox_events_attempts_nonneg
        CHECK (attempts >= 0),

    -- sent_at must be set when SENT, must be NULL otherwise
    CONSTRAINT outbox_events_sent_at_check
        CHECK (
            (status = 'SENT' AND sent_at IS NOT NULL)
            OR status <> 'SENT'
        ),

    -- attempts must be > 0 once we've tried at least once
    CONSTRAINT outbox_events_sent_attempted_check
        CHECK (
            (status IN ('SENT', 'FAILED') AND attempts > 0)
            OR status = 'PENDING'
        )
);

-- Primary relay worker index: SELECT FOR UPDATE SKIP LOCKED on PENDING rows
-- ordered by created_at (oldest first). Partial index — only PENDING rows
-- are relevant to the poller.
CREATE INDEX outbox_events_relay_polling_idx
    ON outbox.outbox_events (created_at ASC)
    WHERE status = 'PENDING';

-- Ops/monitoring index: find all events for a given aggregate
-- ("show me every outbox event for vault deposit X")
CREATE INDEX outbox_events_aggregate_idx
    ON outbox.outbox_events (aggregate_type, aggregate_id, created_at DESC);

-- Ops index: find FAILED events for investigation
CREATE INDEX outbox_events_failed_idx
    ON outbox.outbox_events (created_at DESC)
    WHERE status = 'FAILED';

COMMENT ON TABLE outbox.outbox_events IS
    'Transactional outbox table. Domain events are written here in the same '
    'DB transaction as the business change. The relay worker (v0.3-010) polls '
    'PENDING rows and publishes to RabbitMQ. Schema doc §7.2.';

COMMENT ON COLUMN outbox.outbox_events.payload IS
    'Append-only: never updated after INSERT. Column-level UPDATE revoked '
    'from stash_payments by this migration.';

COMMENT ON COLUMN outbox.outbox_events.attempts IS
    'Incremented by the relay worker on each publish attempt. Capped at 5 '
    '(relay logic, not a DB constraint). At 5 the relay moves the row to '
    'the dead-letter table and sets status=FAILED.';

COMMENT ON COLUMN outbox.outbox_events.routing_key IS
    'RabbitMQ routing key. Format: {domain}.{entity}.{verb} — '
    'e.g. payments.deposit.completed, vault.early_exit.initiated.';

-- ── Column-level append-only enforcement ─────────────────────────────────
--
-- Step 1: Revoke table-level UPDATE from the application role.
--         (DELETE was never granted at table creation so this REVOKE
--          is belt-and-suspenders — it makes the intent explicit.)
REVOKE UPDATE, DELETE ON outbox.outbox_events FROM stash_payments;

-- Step 2: Grant UPDATE back on only the four relay-tracking columns.
--         The relay worker must be able to mark events SENT/FAILED,
--         increment attempts, and set sent_at / last_attempted_at.
--         It must never be able to alter payload, event_type, routing_key,
--         aggregate_id, aggregate_type, schema_version, or created_at.
GRANT UPDATE (status, attempts, sent_at, last_attempted_at)
    ON outbox.outbox_events TO stash_payments;
