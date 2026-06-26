-- Migration: V5__seed_fee_revenue_account.sql
-- Seeds the FEE_REVENUE system ledger account.
--
-- This account collects platform fee revenue — early-exit penalties
-- (v0.3-033) and transaction fees. It is the CREDIT (destination) leg of
-- the internal transfer the early-exit release worker performs when moving
-- a vault's penalty amount out of the vault ledger account.
--
-- The UUID is fixed (00000000-0000-0000-0000-000000000002) so that
-- application configuration (monolith stash.ledger.fee-revenue-account-id)
-- can reference it by value without a lookup query. All environments use
-- the same UUID.
--
-- owner_id = NULL: system accounts have no individual owner.
-- owner_type = 'SYSTEM'; account_type = 'FEE_REVENUE'.
--
-- ON CONFLICT DO NOTHING: idempotent — safe to re-run.

INSERT INTO ledger.ledger_accounts (
    id,
    account_type,
    owner_type,
    owner_id,
    external_reference,
    status,
    description,
    created_at
) VALUES (
    '00000000-0000-0000-0000-000000000002',
    'FEE_REVENUE',
    'SYSTEM',
    NULL,
    NULL,
    'ACTIVE',
    'Platform fee revenue — early-exit penalties and transaction fees.',
    NOW()
) ON CONFLICT (id) DO NOTHING;
