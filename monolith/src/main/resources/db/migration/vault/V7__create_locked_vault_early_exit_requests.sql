-- Migration: V7__create_locked_vault_early_exit_requests.sql
-- Flyway version 7: V6 is vault.vaults (v0.3-025). Versions are GLOBAL across
-- all locations (see db/migration/MIGRATIONS.md) and out-of-order is disabled,
-- so this must be V7 — it also has to run after V6 because of the FK to
-- vault.vaults.
-- Creates vault.locked_vault_early_exit_requests per Schema doc §3.2.
--
-- Design decisions:
--   - Only one PENDING request is permitted per vault at a time. Enforced
--     by a partial unique index on (vault_id) WHERE status = 'PENDING'.
--     A second PENDING INSERT for the same vault_id fails the index
--     immediately — no application-layer race condition possible.
--
--   - release_amount = balance_at_request - penalty_amount enforced at the
--     DB level via CHECK. If the application miscalculates, the INSERT
--     fails rather than writing corrupt financial data silently.
--
--   - penalty_amount ≥ 0: penalties are never negative (no accidental bonuses).
--
--   - release_amount ≥ 0: if the penalty somehow equals or exceeds the
--     balance, release_amount floors at 0. A release_amount < 0 would mean
--     the user owes the platform money on exit — not a v0.3 design.
--
--   - balance_at_request > 0: requesting early exit on an empty vault is
--     a business logic error. The application prevents this, but the DB
--     enforces it as a safety net.
--
--   - scheduled_release_at = created_at + 72 hours is set by the
--     application, not a DEFAULT, for the same reason as idempotency
--     key expires_at: explicit computation avoids clock ambiguity.
--
--   - requested_by_user_id is a logical reference to user_module.users.id
--     (same database, but we use the logical reference pattern for
--     consistency with the vault module's design — the vault FK is physical,
--     the user FK is logical to keep the query simpler during the exit flow).
--     Actually: both live in monolith-db, so a physical FK is valid. Using
--     a physical FK here for data integrity.
--
-- Rollback strategy: forward-only.

CREATE TABLE vault.locked_vault_early_exit_requests (

    id                      UUID            NOT NULL,

    vault_id                UUID            NOT NULL,
    requested_by_user_id    UUID            NOT NULL,

    reason                  VARCHAR(50)     NOT NULL,

    -- Financial snapshot at time of request (all in pesewas)
    balance_at_request      BIGINT          NOT NULL,   -- Vault balance when exit was requested
    penalty_amount          BIGINT          NOT NULL,   -- 5% of balance_at_request
    release_amount          BIGINT          NOT NULL,   -- balance_at_request - penalty_amount

    scheduled_release_at    TIMESTAMPTZ     NOT NULL,   -- created_at + 72 hours; set by app
    status                  VARCHAR(50)     NOT NULL DEFAULT 'PENDING',

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at             TIMESTAMPTZ     NULL,       -- Set when COMPLETED or CANCELLED

    CONSTRAINT locked_vault_early_exit_requests_pkey
        PRIMARY KEY (id),

    CONSTRAINT locked_vault_early_exit_requests_vault_fk
        FOREIGN KEY (vault_id)
        REFERENCES vault.vaults (id)
        ON DELETE RESTRICT,

    CONSTRAINT locked_vault_early_exit_requests_user_fk
        FOREIGN KEY (requested_by_user_id)
        REFERENCES user_module.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT locked_vault_early_exit_requests_reason_check
        CHECK (reason IN (
            'SCHOOL_FEES_EMERGENCY',
            'MEDICAL',
            'FAMILY',
            'OTHER'
        )),

    CONSTRAINT locked_vault_early_exit_requests_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),

    -- Financial integrity: release = balance - penalty
    CONSTRAINT locked_vault_early_exit_requests_release_calculation
        CHECK (release_amount = balance_at_request - penalty_amount),

    -- Sign constraints
    CONSTRAINT locked_vault_early_exit_requests_balance_positive
        CHECK (balance_at_request > 0),

    CONSTRAINT locked_vault_early_exit_requests_penalty_nonneg
        CHECK (penalty_amount >= 0),

    CONSTRAINT locked_vault_early_exit_requests_release_nonneg
        CHECK (release_amount >= 0),

    -- scheduled_release_at must be after created_at (72h ahead)
    CONSTRAINT locked_vault_early_exit_requests_release_after_created
        CHECK (scheduled_release_at > created_at),

    -- resolved_at must be set on terminal states
    CONSTRAINT locked_vault_early_exit_requests_resolved_at_terminal
        CHECK (
            (status IN ('COMPLETED', 'CANCELLED') AND resolved_at IS NOT NULL)
            OR status = 'PENDING'
        )
);

-- THE uniqueness constraint: only one PENDING request per vault.
-- A second PENDING INSERT for the same vault fails immediately.
-- COMPLETED and CANCELLED rows are not covered by this index —
-- a vault may have multiple historical requests, one per early-exit cycle.
CREATE UNIQUE INDEX locked_vault_early_exit_requests_one_pending_per_vault
    ON vault.locked_vault_early_exit_requests (vault_id)
    WHERE status = 'PENDING';

-- Ops query: find all active (PENDING) exit requests, oldest first
-- (used by the early-exit release scheduler)
CREATE INDEX locked_vault_early_exit_requests_pending_release_idx
    ON vault.locked_vault_early_exit_requests (scheduled_release_at ASC)
    WHERE status = 'PENDING';

-- Audit: full request history for a vault
CREATE INDEX locked_vault_early_exit_requests_vault_id_idx
    ON vault.locked_vault_early_exit_requests (vault_id, created_at DESC);

COMMENT ON TABLE vault.locked_vault_early_exit_requests IS
    'Records of user requests to break a locked vault early. '
    'One PENDING row per vault at a time (enforced by partial unique index). '
    'Schema doc §3.2.';

COMMENT ON COLUMN vault.locked_vault_early_exit_requests.penalty_amount IS
    '5% of balance_at_request, rounded down to the nearest pesewa. '
    'Calculated and stored at request time — does not change during the cool-off window.';

COMMENT ON COLUMN vault.locked_vault_early_exit_requests.release_amount IS
    'balance_at_request - penalty_amount. '
    'This is the amount released to the user''s USER_WALLET ledger account '
    'when the cool-off expires and the release worker fires.';

COMMENT ON COLUMN vault.locked_vault_early_exit_requests.scheduled_release_at IS
    'created_at + 72 hours. Set by the application. '
    'The early-exit release worker polls for PENDING rows WHERE '
    'scheduled_release_at <= NOW() and processes them.';

COMMENT ON COLUMN vault.locked_vault_early_exit_requests.resolved_at IS
    'Timestamp when the request reached a terminal state. '
    'NULL while PENDING; set to NOW() when COMPLETED or CANCELLED.';
