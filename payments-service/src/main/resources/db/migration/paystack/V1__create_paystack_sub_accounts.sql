-- Migration: V1__create_paystack_sub_accounts.sql
-- Creates the paystack schema and paystack.paystack_sub_accounts table
-- per Schema doc §7.4.
--
-- Purpose: the bridge between the Paystack world (subaccount codes) and
-- the Stash ledger world (ledger account UUIDs). When Paystack processes
-- a deposit or withdrawal for a user, it uses the subaccount_code to
-- identify the recipient; this table resolves that code back to a
-- ledger_account_id so the ledger write service knows which account to
-- credit or debit.
--
-- Design decisions:
--   - owner_type + owner_id, NOT user_id (despite the issue's column list).
--     The Schema doc §7.4 uses the polymorphic owner pattern consistent
--     with ledger_accounts §6.1 — because some vaults and susu groups
--     also get their own Paystack subaccounts depending on the settlement
--     strategy. A bare user_id column would hard-code the user-only
--     assumption and require a schema change for vault/susu subaccounts.
--     v0.3-014 (provisioning service) writes owner_type='USER' for user
--     wallets. See tracking flag below.
--
--   - FK to ledger.ledger_accounts: valid — same database (payments-db).
--     ON DELETE RESTRICT: destroying a ledger account that has an active
--     Paystack subaccount pointing to it would break settlement. The DB
--     must refuse.
--
--   - UNIQUE on (owner_type, owner_id): one Paystack subaccount per
--     entity type+ID. Prevents a bug in the provisioning service from
--     creating two subaccounts for the same user.
--
--   - metadata JSONB: stores the full Paystack subaccount creation
--     response (business_name, settlement_bank, account_number,
--     percentage_charge, etc.) so we don't need to call Paystack's API
--     again just to display bank info to the user. Updated when Paystack
--     reports changes via webhook.
--
--   - No REVOKE — this table is legitimately updated (status changes
--     ACTIVE → SUSPENDED → CLOSED; metadata updates from Paystack
--     webhooks). Full DML is appropriate.
--
-- Rollback strategy: forward-only.

CREATE SCHEMA IF NOT EXISTS paystack;

CREATE TABLE paystack.paystack_sub_accounts (

    id                          UUID            NOT NULL,

    -- Polymorphic owner: USER, VAULT, or SUSU_GROUP
    owner_type                  VARCHAR(50)     NOT NULL,
    owner_id                    UUID            NOT NULL,   -- Logical reference — no FK across services

    paystack_subaccount_code    VARCHAR(255)    NOT NULL,   -- e.g. ACCT_xxxxxxxxxx

    -- FK to the ledger account this subaccount funds
    ledger_account_id           UUID            NOT NULL,

    status                      VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',

    -- Full Paystack subaccount creation response and any subsequent updates
    metadata                    JSONB           NULL,

    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT paystack_sub_accounts_pkey
        PRIMARY KEY (id),

    -- No two Paystack subaccounts may share a code
    CONSTRAINT paystack_sub_accounts_code_uk
        UNIQUE (paystack_subaccount_code),

    -- One subaccount per entity — prevents duplicate provisioning
    CONSTRAINT paystack_sub_accounts_owner_uk
        UNIQUE (owner_type, owner_id),

    CONSTRAINT paystack_sub_accounts_ledger_account_fk
        FOREIGN KEY (ledger_account_id)
        REFERENCES ledger.ledger_accounts (id)
        ON DELETE RESTRICT,

    CONSTRAINT paystack_sub_accounts_owner_type_check
        CHECK (owner_type IN ('USER', 'VAULT', 'SUSU_GROUP')),

    CONSTRAINT paystack_sub_accounts_status_check
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

-- Resolve a Paystack subaccount code back to a ledger account
-- (the critical lookup in the webhook handler — charge.success arrives
-- with the subaccount_code; this index makes the resolution O(log n))
CREATE INDEX paystack_sub_accounts_code_idx
    ON paystack.paystack_sub_accounts (paystack_subaccount_code);
-- Note: the UNIQUE constraint already creates a btree index on this column.
-- This explicit index declaration is redundant but left here as documentation
-- of intent — remove if the linter flags it; the UNIQUE index covers it.

-- Resolve owner → subaccount (used by the deposit endpoint to find the
-- subaccount code for a given user before calling Paystack)
CREATE INDEX paystack_sub_accounts_owner_idx
    ON paystack.paystack_sub_accounts (owner_type, owner_id);
-- Note: same situation as above — the UNIQUE (owner_type, owner_id)
-- constraint creates this index. Kept for documentation clarity.

COMMENT ON TABLE paystack.paystack_sub_accounts IS
    'Bridge between Paystack subaccount codes and Stash ledger account IDs. '
    'Every entity that receives Paystack settlements (users, vaults, susu groups) '
    'has one row here. Schema doc §7.4.';

COMMENT ON COLUMN paystack.paystack_sub_accounts.owner_type IS
    'USER, VAULT, or SUSU_GROUP. Polymorphic owner — no FK across services. '
    'v0.3 only provisions USER subaccounts (USER_WALLET accounts). '
    'VAULT and SUSU_GROUP subaccounts are a future settlement strategy option.';

COMMENT ON COLUMN paystack.paystack_sub_accounts.paystack_subaccount_code IS
    'Paystack-assigned code, e.g. ACCT_xxxxxxxxxx. This is the identifier '
    'Paystack uses in charge and transfer calls. UNIQUE enforced at DB level.';

COMMENT ON COLUMN paystack.paystack_sub_accounts.ledger_account_id IS
    'FK to ledger.ledger_accounts.id — the Stash ledger account this '
    'Paystack subaccount funds. For USER subaccounts this is always the '
    'USER_WALLET account for that user.';

COMMENT ON COLUMN paystack.paystack_sub_accounts.metadata IS
    'Full Paystack subaccount response body stored as JSONB. Contains '
    'business_name, settlement_bank, account_number, percentage_charge. '
    'Updated by the Paystack webhook handler when Paystack reports changes.';
