-- Migration: V1__create_ledger_accounts.sql
-- Creates the ledger schema and ledger.ledger_accounts table per Schema
-- doc §6.1. This is the foundation of the Payments Service — every entity
-- on the platform that holds a balance (user wallets, vaults, susu pots,
-- Paystack subaccounts, system revenue accounts) is represented as a row
-- in this table.
--
-- Design decisions:
--   - owner_id is NULL for SYSTEM accounts (FEE_REVENUE, PENALTY_REVENUE,
--     PAYSTACK_SETTLEMENT). Because UNIQUE in PostgreSQL treats NULLs as
--     distinct, the (owner_type, owner_id, account_type) UNIQUE constraint
--     alone would NOT prevent two FEE_REVENUE rows. A partial unique index
--     on (owner_type, account_type) WHERE owner_id IS NULL closes that gap
--     for SYSTEM accounts specifically. Both indexes coexist; their domains
--     don't overlap because the partial index only applies when owner_id
--     IS NULL, and the main UNIQUE is only meaningful when owner_id IS NOT
--     NULL (NULL rows are distinct under that constraint anyway).
--   - account_type, owner_type, and status use VARCHAR + CHECK rather than
--     Postgres ENUM types — no ALTER TYPE lock when v3.0 adds
--     PARTNER_CUSTODY, and tooling (Hibernate, jOOQ) plays better with
--     VARCHAR.
--   - No FK from owner_id to anything — owner_id may reference a user
--     (monolith-db), a vault (monolith-db), a susu_group (monolith-db),
--     or be NULL for SYSTEM accounts. Cross-database FKs are impossible;
--     the reference is logical only.
--   - balance is NOT stored on this row. The authoritative balance is
--     SUM(credits) - SUM(debits) over ledger.ledger_entries (§6.3). The
--     application caches the computed balance — caching lives outside
--     this schema (see Module Boundaries doc).
--
-- Rollback strategy: forward-only.

CREATE SCHEMA IF NOT EXISTS ledger;

CREATE TABLE ledger.ledger_accounts (

    id                  UUID            NOT NULL,

    account_type        VARCHAR(50)     NOT NULL,
    owner_type          VARCHAR(50)     NOT NULL,
    owner_id            UUID            NULL,        -- NULL only when owner_type = 'SYSTEM'

    external_reference  VARCHAR(255)    NULL,        -- e.g. Paystack subaccount code
    status              VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    description         VARCHAR(255)    NULL,        -- Human-readable label for admins

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT ledger_accounts_pkey
        PRIMARY KEY (id),

    CONSTRAINT ledger_accounts_account_type_check
        CHECK (account_type IN (
            'USER_WALLET',
            'VAULT',
            'SUSU_POT',
            'FEE_REVENUE',
            'PENALTY_REVENUE',
            'PAYSTACK_SETTLEMENT'
        )),

    CONSTRAINT ledger_accounts_owner_type_check
        CHECK (owner_type IN ('USER', 'VAULT', 'SUSU_GROUP', 'SYSTEM')),

    CONSTRAINT ledger_accounts_status_check
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),

    -- SYSTEM accounts have NULL owner_id; everything else must have one.
    CONSTRAINT ledger_accounts_owner_id_system_rule
        CHECK (
            (owner_type = 'SYSTEM' AND owner_id IS NULL)
            OR
            (owner_type <> 'SYSTEM' AND owner_id IS NOT NULL)
        ),

    -- One account of a given type per owner (NULL owner_id slips through here;
    -- the partial index below covers the SYSTEM case).
    CONSTRAINT ledger_accounts_unique_owner_account_type
        UNIQUE (owner_type, owner_id, account_type)
);

-- Partial unique index covering SYSTEM accounts, where owner_id IS NULL and
-- the table-level UNIQUE doesn't apply. Prevents two FEE_REVENUE rows etc.
CREATE UNIQUE INDEX ledger_accounts_unique_system_account_type
    ON ledger.ledger_accounts (owner_type, account_type)
    WHERE owner_id IS NULL;

-- Schema doc §6.1 index: find the account(s) for a given owner.
CREATE INDEX ledger_accounts_owner_idx
    ON ledger.ledger_accounts (owner_type, owner_id);

-- Schema doc §6.1 index: operational queries ('show all frozen vaults', etc.)
CREATE INDEX ledger_accounts_account_type_status_idx
    ON ledger.ledger_accounts (account_type, status);

COMMENT ON TABLE ledger.ledger_accounts IS
    'Every pocket of money on the platform. owner_id is a LOGICAL reference '
    '(no FK) — may point to monolith-db.user_module.users.id, '
    'monolith-db.vault.vaults.id, monolith-db.susu.susu_groups.id, or NULL '
    'for SYSTEM accounts. Schema doc §6.1.';

COMMENT ON COLUMN ledger.ledger_accounts.owner_id IS
    'Logical reference to the owning entity. NULL only when owner_type = SYSTEM. '
    'Enforced by ledger_accounts_owner_id_system_rule CHECK.';

COMMENT ON COLUMN ledger.ledger_accounts.external_reference IS
    'External-system identifier — for USER_WALLET accounts this is the '
    'Paystack subaccount code once provisioned (v0.3-009). NULL for accounts '
    'with no external mapping.';

COMMENT ON CONSTRAINT ledger_accounts_unique_owner_account_type
    ON ledger.ledger_accounts IS
    'One account per (owner, account_type). Works for NON-SYSTEM rows; '
    'SYSTEM uniqueness is enforced by the partial index '
    'ledger_accounts_unique_system_account_type.';
