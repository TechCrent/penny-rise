-- Migration: V1__create_processed_webhook_events.sql
-- Creates the webhook schema and webhook.processed_webhook_events table
-- per Schema doc §7.3.
--
-- Purpose: deduplication store for inbound Paystack webhook deliveries.
-- Paystack delivers with at-least-once semantics — the same event_id
-- may arrive more than once. The UNIQUE constraint on (provider, event_id)
-- is the race condition resolver: two simultaneous deliveries of the
-- same event both attempt INSERT; one wins, one gets a unique constraint
-- violation that the handler converts to 200 OK no-op without doing any
-- work. This is safe because the INSERT and the ledger writes happen in
-- one @Transactional block — if the ledger write fails, the dedup row
-- rolls back too and the next delivery gets a clean slate.
--
-- Design decisions:
--   - payload_hash: SHA-256 of the raw webhook body, hex-encoded.
--     Stored for audit — if Paystack's payload for the same event_id
--     ever differs between deliveries, the mismatch is detectable from
--     this column without replaying the raw payload.
--   - signature_valid: the HMAC verification result recorded as-is.
--     Events with signature_valid = false are still inserted (so we
--     know we received them) but set processing_status = IGNORED and
--     never processed. This gives ops a clear audit trail of rejected
--     deliveries without silently discarding them.
--   - processing_status covers four states: PENDING (inserted, not yet
--     processed — brief window between dedup-insert and handler
--     completion), COMPLETED (ledger written successfully), FAILED
--     (handler threw; ledger NOT written — row rolled back and re-inserted
--     on retry), IGNORED (signature invalid or event_type not handled).
--   - resulting_transaction_id: logical reference to
--     transaction.transactions.id — set after a successful ledger write.
--     NULL for IGNORED and FAILED rows. Not a FK because if the ledger
--     write and the dedup INSERT are in one transaction, the transaction
--     row is committed atomically and the FK would always be satisfiable
--     at commit time — but we keep it logical to avoid the cross-schema
--     FK maintenance cost.
--   - received_at vs processed_at: received_at is DEFAULT NOW() set at
--     INSERT (when the webhook arrived). processed_at is NULL until the
--     handler completes — set by the UPDATE after the ledger write
--     commits. The gap between them is the processing latency, visible
--     to ops without log diving.
--   - No REVOKE — this table is legitimately updated (PENDING →
--     COMPLETED/FAILED/IGNORED) and the dedup rows are never deleted
--     (they are the permanent audit trail of every webhook we received).
--
-- Rollback strategy: forward-only.

CREATE SCHEMA IF NOT EXISTS webhook;

CREATE TABLE webhook.processed_webhook_events (

    id                          UUID            NOT NULL,

    provider                    VARCHAR(50)     NOT NULL,   -- PAYSTACK (expandable)
    event_id                    VARCHAR(255)    NOT NULL,   -- Provider's event identifier
    event_type                  VARCHAR(100)    NOT NULL,   -- e.g. charge.success, transfer.failed

    payload_hash                VARCHAR(255)    NOT NULL,   -- SHA-256(raw body), hex-encoded; audit trail
    signature_valid             BOOLEAN         NOT NULL,   -- HMAC verification result

    processing_status           VARCHAR(50)     NOT NULL DEFAULT 'PENDING',

    -- Logical reference to transaction.transactions.id; NULL until COMPLETED
    resulting_transaction_id    UUID            NULL,

    correlation_id              VARCHAR(255)    NULL,

    received_at                 TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    processed_at                TIMESTAMPTZ     NULL,       -- NULL until terminal state

    CONSTRAINT processed_webhook_events_pkey
        PRIMARY KEY (id),

    -- THE deduplication constraint. Two deliveries of the same event_id
    -- from the same provider: first INSERT wins, second gets a constraint
    -- violation → 200 OK no-op.
    CONSTRAINT processed_webhook_events_provider_event_id_uk
        UNIQUE (provider, event_id),

    CONSTRAINT processed_webhook_events_provider_check
        CHECK (provider IN ('PAYSTACK')),

    CONSTRAINT processed_webhook_events_processing_status_check
        CHECK (processing_status IN ('PENDING', 'COMPLETED', 'FAILED', 'IGNORED')),

    -- processed_at must be set when terminal; NULL when PENDING
    CONSTRAINT processed_webhook_events_processed_at_check
        CHECK (
            (processing_status IN ('COMPLETED', 'FAILED', 'IGNORED')
                AND processed_at IS NOT NULL)
            OR processing_status = 'PENDING'
        ),

    -- resulting_transaction_id only set on COMPLETED
    CONSTRAINT processed_webhook_events_transaction_id_check
        CHECK (
            (resulting_transaction_id IS NOT NULL
                AND processing_status = 'COMPLETED')
            OR resulting_transaction_id IS NULL
        )
);

-- Covered by the UNIQUE constraint — no separate index needed for
-- (provider, event_id) lookups.

-- Ops query: "show me all FAILED or PENDING webhooks in the last hour"
-- Also used by the stuck-webhook monitor in the nightly integrity job
CREATE INDEX processed_webhook_events_status_received_at_idx
    ON webhook.processed_webhook_events (processing_status, received_at DESC)
    WHERE processing_status IN ('PENDING', 'FAILED');

-- Ops query: "was this Paystack transaction reference processed?" —
-- resolved via resulting_transaction_id without a join to transactions
CREATE INDEX processed_webhook_events_resulting_transaction_idx
    ON webhook.processed_webhook_events (resulting_transaction_id)
    WHERE resulting_transaction_id IS NOT NULL;

COMMENT ON TABLE webhook.processed_webhook_events IS
    'Deduplication store for inbound Paystack webhook deliveries. '
    'UNIQUE (provider, event_id) is the race condition resolver — '
    'duplicate deliveries lose the INSERT race and are discarded as '
    '200 OK no-ops. Schema doc §7.3.';

COMMENT ON COLUMN webhook.processed_webhook_events.signature_valid IS
    'HMAC verification result. False does not prevent INSERT — we record '
    'the delivery and set processing_status = IGNORED. This gives ops '
    'an audit trail of rejected deliveries rather than silently discarding them.';

COMMENT ON COLUMN webhook.processed_webhook_events.payload_hash IS
    'SHA-256 of the raw HTTP request body, hex-encoded. Stored for audit: '
    'if the same event_id arrives with a different payload in a later '
    'delivery, the mismatch is detectable from this column.';

COMMENT ON COLUMN webhook.processed_webhook_events.resulting_transaction_id IS
    'Logical reference to transaction.transactions.id. Set only when '
    'processing_status = COMPLETED and a ledger transaction was produced. '
    'No FK — logical reference to avoid cross-schema FK maintenance.';

COMMENT ON COLUMN webhook.processed_webhook_events.received_at IS
    'Timestamp when the webhook arrived at our endpoint (INSERT time). '
    'processed_at - received_at = handler latency, visible to ops.';
