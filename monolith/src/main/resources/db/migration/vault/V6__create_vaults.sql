-- Migration: V6__create_vaults.sql
-- Creates the vault schema and vault.vaults table per Schema doc §3.1.
--
-- Design decisions:
--   - STANDARD vs LOCKED enforcement is at the DB level via two CHECK
--     constraints. Application code must satisfy both; a bug that sets
--     unlock_by_date on a STANDARD vault is caught immediately, not
--     silently allowed and discovered later as corrupt data.
--
--   - unlock_condition_logic is NULL unless both unlock columns are set.
--     This is the only case where "AND" vs "OR" is meaningful. Setting it
--     when only one unlock column is present would be misleading and is
--     rejected by the CHECK constraint.
--
--   - ledger_account_id is a logical reference to ledger.ledger_accounts.id
--     in payments-db. No FK is possible across databases. The application
--     layer ensures the ledger account exists (provisioned via the Payments
--     Service internal transfer API) before inserting the vault row.
--
--   - owner_user_id FK to user_module.users.id is a physical FK — both
--     tables live in monolith-db. ON DELETE RESTRICT prevents deleting a
--     user who still has vaults.
--
--   - deleted_at is soft-delete. A vault is never hard-deleted; it is
--     CLOSED and soft-deleted. Historical data and audit trails must remain.
--
-- Rollback strategy: forward-only.

CREATE SCHEMA IF NOT EXISTS vault;

CREATE TABLE vault.vaults (

    id                      UUID            NOT NULL,

    owner_user_id           UUID            NOT NULL,
    name                    VARCHAR(100)    NOT NULL,

    vault_type              VARCHAR(20)     NOT NULL,
    status                  VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',

    -- Cross-service logical reference to ledger.ledger_accounts.id
    -- in payments-db. No FK constraint.
    ledger_account_id       UUID            NOT NULL,

    -- Unlock conditions — LOCKED vaults only
    unlock_by_date          TIMESTAMPTZ     NULL,   -- Target date to auto-unlock
    unlock_target_amount    BIGINT          NULL,   -- Target savings amount in pesewas
    unlock_condition_logic  VARCHAR(10)     NULL,   -- 'AND' or 'OR'; NULL unless both columns set

    early_exit_in_progress  BOOLEAN         NOT NULL DEFAULT FALSE,

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ     NULL,   -- Soft delete; NULL = not deleted

    CONSTRAINT vaults_pkey
        PRIMARY KEY (id),

    CONSTRAINT vaults_owner_user_fk
        FOREIGN KEY (owner_user_id)
        REFERENCES user_module.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT vaults_vault_type_check
        CHECK (vault_type IN ('STANDARD', 'LOCKED')),

    CONSTRAINT vaults_status_check
        CHECK (status IN ('ACTIVE', 'LOCKED', 'EARLY_EXIT_PENDING', 'CLOSED')),

    -- STANDARD vault: neither unlock condition may be set
    CONSTRAINT vaults_standard_no_unlock_conditions
        CHECK (
            vault_type <> 'STANDARD'
            OR (unlock_by_date IS NULL AND unlock_target_amount IS NULL)
        ),

    -- LOCKED vault: at least one unlock condition must be set
    CONSTRAINT vaults_locked_requires_unlock_condition
        CHECK (
            vault_type <> 'LOCKED'
            OR (unlock_by_date IS NOT NULL OR unlock_target_amount IS NOT NULL)
        ),

    -- unlock_condition_logic: only set when both unlock columns are populated;
    -- must be NULL when only one or neither unlock column is set
    CONSTRAINT vaults_unlock_logic_requires_both_conditions
        CHECK (
            unlock_condition_logic IS NULL
            OR (unlock_by_date IS NOT NULL AND unlock_target_amount IS NOT NULL)
        ),

    -- unlock_condition_logic values: AND, OR, or NULL
    CONSTRAINT vaults_unlock_condition_logic_check
        CHECK (unlock_condition_logic IS NULL
               OR unlock_condition_logic IN ('AND', 'OR')),

    -- early_exit_in_progress is only meaningful on LOCKED vaults
    CONSTRAINT vaults_early_exit_locked_only
        CHECK (
            early_exit_in_progress = FALSE
            OR vault_type = 'LOCKED'
        ),

    -- unlock_target_amount must be positive if set
    CONSTRAINT vaults_unlock_target_amount_positive
        CHECK (unlock_target_amount IS NULL OR unlock_target_amount > 0)
);

-- Schema doc §3.1: list user's vaults with status filter
CREATE INDEX vaults_owner_user_id_status_idx
    ON vault.vaults (owner_user_id, status)
    WHERE deleted_at IS NULL;

-- Schema doc §3.1: look up which vault a ledger account belongs to
-- (used by the Payments Service webhook handler to route deposits)
CREATE UNIQUE INDEX vaults_ledger_account_id_idx
    ON vault.vaults (ledger_account_id)
    WHERE deleted_at IS NULL;

-- Soft-delete support: ops queries for all vaults including deleted
CREATE INDEX vaults_owner_user_id_all_idx
    ON vault.vaults (owner_user_id, created_at DESC);

COMMENT ON TABLE vault.vaults IS
    'User savings vaults. STANDARD vaults have no lock conditions; '
    'LOCKED vaults require at least one unlock condition (date or target amount). '
    'Schema doc §3.1.';

COMMENT ON COLUMN vault.vaults.ledger_account_id IS
    'Logical reference to ledger.ledger_accounts.id in payments-db. '
    'No FK constraint — cross-database reference. The Payments Service '
    'provisions the ledger account before the vault row is inserted.';

COMMENT ON COLUMN vault.vaults.unlock_condition_logic IS
    'AND: both unlock_by_date AND unlock_target_amount must be met to auto-unlock. '
    'OR: either condition suffices. NULL when only one condition is set (logic is trivial).';

COMMENT ON COLUMN vault.vaults.early_exit_in_progress IS
    'TRUE during the 72-hour cool-off after a user requests early exit from a LOCKED vault. '
    'Flipped back to FALSE if the user cancels before the cool-off expires. '
    'Only meaningful when vault_type = LOCKED.';

COMMENT ON COLUMN vault.vaults.deleted_at IS
    'Soft delete timestamp. NULL = not deleted. '
    'Vaults are never hard-deleted — historical data must be preserved.';
