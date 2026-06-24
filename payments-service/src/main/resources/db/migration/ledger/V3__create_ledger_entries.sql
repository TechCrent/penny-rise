-- Migration: V3__create_ledger_entries.sql
-- Creates ledger.ledger_entries per Schema doc §6.3 and enforces
-- append-only semantics at the Postgres role level.
--
-- Design decisions:
--   - FK to ledger.ledger_transactions: valid — same database, same schema.
--     ON DELETE RESTRICT (not CASCADE) because deleting a ledger_transaction
--     is never valid; if we tried, the entries would block it, which is the
--     correct fail-safe.
--   - FK to ledger.ledger_accounts: valid — same database.
--     ON DELETE RESTRICT for the same reason: you must never destroy an
--     account that has posted entries.
--   - amount uses CHECK > 0. The SIGN of the amount is carried by
--     direction (DEBIT/CREDIT), not by a negative number. This matches
--     the Money type convention from v0.1-010.
--   - No soft-delete, no updated_at — this table is strictly append-only.
--   - The REVOKE at the end targets 'stash_payments' — the application
--     role created in v0.1-006. If your environment uses a different role
--     name, adjust accordingly and update the startup check constant.
--
-- Rollback strategy: forward-only. The REVOKE cannot be rolled back
-- without a compensating GRANT, which itself requires a superuser — by
-- design. Rolling back append-only enforcement requires deliberate
-- human action.

CREATE TABLE ledger.ledger_entries (

    id                      UUID            NOT NULL,

    ledger_transaction_id   UUID            NOT NULL,
    account_id              UUID            NOT NULL,

    direction               VARCHAR(10)     NOT NULL,   -- DEBIT or CREDIT
    amount                  BIGINT          NOT NULL,   -- Pesewas; CHECK > 0

    narrative               VARCHAR(255)    NULL,       -- Optional per-entry note

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT ledger_entries_pkey
        PRIMARY KEY (id),

    CONSTRAINT ledger_entries_transaction_fk
        FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger.ledger_transactions (id)
        ON DELETE RESTRICT,

    CONSTRAINT ledger_entries_account_fk
        FOREIGN KEY (account_id)
        REFERENCES ledger.ledger_accounts (id)
        ON DELETE RESTRICT,

    CONSTRAINT ledger_entries_direction_check
        CHECK (direction IN ('DEBIT', 'CREDIT')),

    CONSTRAINT ledger_entries_amount_positive
        CHECK (amount > 0)
);

-- Schema doc §6.3 index: account statement query
-- ("show me all entries for this account, newest first")
CREATE INDEX ledger_entries_account_id_created_at_idx
    ON ledger.ledger_entries (account_id, created_at DESC);

-- Schema doc §6.3 index: fetch all entries for a transaction
-- (used by the double-entry invariant check and the nightly integrity job)
CREATE INDEX ledger_entries_transaction_id_idx
    ON ledger.ledger_entries (ledger_transaction_id);

COMMENT ON TABLE ledger.ledger_entries IS
    'Immutable double-entry bookkeeping records. INSERT and SELECT only — '
    'UPDATE and DELETE are revoked from the application role (stash_payments) '
    'by this migration. Schema doc §6.3.';

COMMENT ON COLUMN ledger.ledger_entries.direction IS
    'DEBIT decreases the account balance; CREDIT increases it. '
    'The amount is always positive — direction carries the sign.';

COMMENT ON COLUMN ledger.ledger_entries.amount IS
    'Pesewas (BIGINT). CHECK amount > 0. Matches the Money type convention '
    '(v0.1-010): never negative, never DECIMAL, never FLOAT.';

-- ── Append-only enforcement ───────────────────────────────────────────────
-- Revoke UPDATE and DELETE from the application role. INSERT and SELECT
-- remain (granted implicitly by table ownership at creation time).
-- This cannot be undone without a superuser GRANT — by design.

REVOKE UPDATE, DELETE ON ledger.ledger_entries FROM stash_payments;
