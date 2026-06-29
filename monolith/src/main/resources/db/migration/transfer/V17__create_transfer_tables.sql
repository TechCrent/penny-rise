-- ============================================================
-- v0.4-013 : transfer schema and its two tables
--
-- Schema doc §4.1 (peer_transfers) and §4.2 (monthly_transfer_quotas).
--
-- Two deviations from the issue description reconciled against Schema doc:
--
-- 1. Column set: the issue lists 8 columns; Schema doc §4.1 specifies 12.
--    Building the full Schema doc column set. Missing columns from issue
--    description: status, note, transaction_id, idempotency_key,
--    counted_against_free_quota, completed_at.
--
-- 2. year_month: the issue description uses a single "year_month" field
--    (e.g. 2506). Schema doc §4.2 uses separate year INT and month INT
--    columns with UNIQUE (user_id, year, month). Building per Schema doc.
--    The v0.4-014 service layer computes year and month from clock at
--    transfer time — no parsing of composite strings needed.
-- ============================================================

CREATE SCHEMA IF NOT EXISTS transfer;

-- ── §4.1 peer_transfers ────────────────────────────────────────────────────

CREATE TABLE transfer.peer_transfers (
    id                         UUID          NOT NULL DEFAULT gen_random_uuid(),
    sender_user_id             UUID          NOT NULL,
    recipient_user_id          UUID          NOT NULL,
    amount                     BIGINT        NOT NULL,
    fee_amount                 BIGINT        NOT NULL DEFAULT 0,
    status                     VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    note                       VARCHAR(255)  NULL,
    transaction_id             UUID          NULL,
    idempotency_key            VARCHAR(255)  NOT NULL,
    counted_against_free_quota BOOLEAN       NOT NULL DEFAULT false,
    completed_at               TIMESTAMPTZ   NULL,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT peer_transfers_pk
        PRIMARY KEY (id),

    CONSTRAINT peer_transfers_idempotency_key_uk
        UNIQUE (idempotency_key),

    CONSTRAINT peer_transfers_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),

    CONSTRAINT peer_transfers_amount_positive
        CHECK (amount > 0),

    CONSTRAINT peer_transfers_fee_non_negative
        CHECK (fee_amount >= 0),

    CONSTRAINT peer_transfers_sender_not_recipient
        CHECK (sender_user_id <> recipient_user_id),

    CONSTRAINT peer_transfers_completed_at_terminal
        CHECK (
            completed_at IS NULL
            OR status IN ('COMPLETED', 'FAILED')
        ),

    CONSTRAINT peer_transfers_transaction_id_terminal
        CHECK (
            transaction_id IS NULL
            OR status IN ('COMPLETED', 'FAILED')
        )
);

COMMENT ON TABLE transfer.peer_transfers IS
    'One row per peer-to-peer money transfer. Created in PENDING status; '
    'transitions to COMPLETED on ledger confirmation or FAILED on error. '
    'Each user gets 5 free transfers per calendar month; '
    'the 6th+ incur a flat GHS 2 fee tracked in fee_amount.';

COMMENT ON COLUMN transfer.peer_transfers.amount IS
    'Principal amount to transfer, in pesewas. Does not include fee_amount.';
COMMENT ON COLUMN transfer.peer_transfers.fee_amount IS
    '0 for free-quota transfers; GHS 2 (200 pesewas) for paid-tier transfers.';
COMMENT ON COLUMN transfer.peer_transfers.status IS
    'PENDING: in-flight. COMPLETED: ledger confirmed. FAILED: not completed.';
COMMENT ON COLUMN transfer.peer_transfers.transaction_id IS
    'Logical FK -> Payments Service transaction record (UUID). '
    'Set on COMPLETED. Cross-service reference — no physical FK.';
COMMENT ON COLUMN transfer.peer_transfers.idempotency_key IS
    'Client-supplied UUID; UNIQUE. Prevents double-submission on network retry.';
COMMENT ON COLUMN transfer.peer_transfers.counted_against_free_quota IS
    'true if this transfer consumed a free-quota slot. '
    'Set at COMPLETED time. Used for analytics: distinguishes '
    '"was free at time of transfer" from the current quota state.';
COMMENT ON COLUMN transfer.peer_transfers.note IS
    'Optional message from sender to recipient (visible in both transaction histories).';

-- Indexes per Schema doc §4.1
CREATE INDEX peer_transfers_sender_idx
    ON transfer.peer_transfers (sender_user_id, created_at DESC);

COMMENT ON INDEX transfer.peer_transfers_sender_idx IS
    'Supports "my sent transfers" list — ordered newest first.';

CREATE INDEX peer_transfers_recipient_idx
    ON transfer.peer_transfers (recipient_user_id, created_at DESC);

COMMENT ON INDEX transfer.peer_transfers_recipient_idx IS
    'Supports "my received transfers" list — ordered newest first.';

-- idempotency_key index is covered by the unique constraint above.

-- ── §4.2 monthly_transfer_quotas ──────────────────────────────────────────

CREATE TABLE transfer.monthly_transfer_quotas (
    id                    UUID  NOT NULL DEFAULT gen_random_uuid(),
    user_id               UUID  NOT NULL,
    year                  INT   NOT NULL,
    month                 INT   NOT NULL,
    free_transfers_used   INT   NOT NULL DEFAULT 0,
    paid_transfers_count  INT   NOT NULL DEFAULT 0,

    CONSTRAINT monthly_transfer_quotas_pk
        PRIMARY KEY (id),

    CONSTRAINT monthly_transfer_quotas_user_month_uk
        UNIQUE (user_id, year, month),

    CONSTRAINT monthly_transfer_quotas_year_valid
        CHECK (year >= 2024),

    CONSTRAINT monthly_transfer_quotas_month_valid
        CHECK (month BETWEEN 1 AND 12),

    CONSTRAINT monthly_transfer_quotas_free_used_non_negative
        CHECK (free_transfers_used >= 0),

    CONSTRAINT monthly_transfer_quotas_paid_count_non_negative
        CHECK (paid_transfers_count >= 0)
);

COMMENT ON TABLE transfer.monthly_transfer_quotas IS
    'Tracks each user''s per-calendar-month transfer quota usage. '
    'Created lazily on the user''s first transfer of the month. '
    'free_transfers_used < 5 → no fee; >= 5 → GHS 2 fee applies per Decision 22.';

COMMENT ON COLUMN transfer.monthly_transfer_quotas.year IS
    'Calendar year. e.g. 2026.';
COMMENT ON COLUMN transfer.monthly_transfer_quotas.month IS
    'Calendar month, 1–12. e.g. 6 for June.';
COMMENT ON COLUMN transfer.monthly_transfer_quotas.free_transfers_used IS
    'How many free-quota transfers this user has completed this month. '
    'Incremented atomically at transfer completion. '
    'The limit (currently 5) is a service constant, not stored per row.';
COMMENT ON COLUMN transfer.monthly_transfer_quotas.paid_transfers_count IS
    'How many paid (over-quota) transfers this user has completed this month. '
    'Informational — used for analytics and the user''s transfer history screen.';
