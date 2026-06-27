-- Migration: V2__create_outbox_dead_letters.sql
-- Dead-letter table for outbox events that exhausted all relay attempts.
-- The relay moves rows here (INSERT + status=FAILED on the source row)
-- rather than deleting them. Ops can inspect, re-queue, or discard.
--
-- This is a separate table rather than a status value on outbox_events
-- because the Schema doc §7.2 CHECK constraint only allows
-- PENDING/SENT/FAILED -- no DEAD value exists.
--
-- Design decisions:
--   - Copies the full event content from outbox_events so the dead-letter
--     row is self-contained even if the source outbox row is later archived.
--   - failed_reason stores the last exception message for ops triage.
--   - requeue_requested: set by ops tooling when a dead-letter event should
--     be re-inserted into outbox_events. The relay does NOT automatically
--     re-process these -- human review is required first.
--
-- Rollback strategy: forward-only.

CREATE TABLE outbox.outbox_dead_letters (

    id                  UUID            NOT NULL,

    -- Copied from the source outbox_events row
    original_event_id   UUID            NOT NULL,
    event_type          VARCHAR(100)    NOT NULL,
    schema_version      VARCHAR(20)     NOT NULL,
    aggregate_type      VARCHAR(100)    NOT NULL,
    aggregate_id        UUID            NOT NULL,
    payload             JSONB           NOT NULL,
    routing_key         VARCHAR(255)    NOT NULL,
    correlation_id      VARCHAR(255)    NULL,
    original_created_at TIMESTAMPTZ     NOT NULL,

    -- Dead-letter metadata
    failed_attempts     INT             NOT NULL,
    failed_reason       TEXT            NULL,   -- last exception message
    dead_lettered_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    requeue_requested   BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT outbox_dead_letters_pkey
        PRIMARY KEY (id),

    CONSTRAINT outbox_dead_letters_original_event_id_uk
        UNIQUE (original_event_id)   -- one dead-letter per source row
);

-- Ops query: "show me all dead-letter events for a given aggregate"
CREATE INDEX outbox_dead_letters_aggregate_idx
    ON outbox.outbox_dead_letters (aggregate_type, aggregate_id);

-- Ops query: "show me unreviewed dead-letters"
CREATE INDEX outbox_dead_letters_requeue_idx
    ON outbox.outbox_dead_letters (dead_lettered_at DESC)
    WHERE requeue_requested = FALSE;

COMMENT ON TABLE outbox.outbox_dead_letters IS
    'Dead-letter store for outbox events that exhausted 5 relay attempts. '
    'Rows arrive here via INSERT in the same transaction that sets '
    'outbox_events.status = FAILED. Ops reviews, fixes the root cause, '
    'and sets requeue_requested = TRUE to signal re-insertion.';

COMMENT ON COLUMN outbox.outbox_dead_letters.requeue_requested IS
    'Set by ops tooling when the event should be re-queued. '
    'The relay does not automatically re-process these -- human sign-off required.';
