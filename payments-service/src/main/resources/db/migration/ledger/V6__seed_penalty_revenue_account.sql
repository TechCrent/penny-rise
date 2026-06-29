-- Migration: V6__seed_penalty_revenue_account.sql
-- Seeds the PENALTY_REVENUE system ledger account.
--
-- This account collects the platform's half of the susu late-payment
-- penalty split (v0.4-011). The other half goes to the affected round's
-- SUSU_POT as compensation to the group. Kept separate from FEE_REVENUE
-- (V5) so susu penalty revenue is distinguishable from early-exit penalties
-- and transaction fees in reporting.
--
-- The UUID is fixed (00000000-0000-0000-0000-000000000003) so that
-- application configuration (monolith stash.ledger.penalty-revenue-account-id)
-- can reference it by value without a lookup query. All environments use
-- the same UUID.
--
-- owner_id = NULL: system accounts have no individual owner.
-- owner_type = 'SYSTEM'; account_type = 'PENALTY_REVENUE'.
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
    '00000000-0000-0000-0000-000000000003',
    'PENALTY_REVENUE',
    'SYSTEM',
    NULL,
    NULL,
    'ACTIVE',
    'Platform penalty revenue — susu late-payment fees (50% share).',
    NOW()
) ON CONFLICT (id) DO NOTHING;
