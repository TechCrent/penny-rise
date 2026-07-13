-- Migration: V8__add_moolre_settlement_account.sql
-- Widens ledger.ledger_accounts.account_type to include MOOLRE_SETTLEMENT
-- and seeds the MOOLRE_SETTLEMENT system account.
--
-- This account represents funds held at Moolre (deposits received by Moolre
-- on our behalf and not yet settled to our bank). It is the DEBIT leg of
-- every Moolre payment-success double-entry — money moves from here to the
-- user's USER_WALLET (CREDIT).
--
-- The UUID is fixed (00000000-0000-0000-0000-000000000004) so that
-- application.yml / environment configuration can reference it by value
-- without a lookup query. All environments use the same UUID.
--
-- Note: 0001 = PAYSTACK_SETTLEMENT, 0002 = FEE_REVENUE, 0003 = PENALTY_REVENUE.
--
-- ON CONFLICT DO NOTHING: idempotent — safe to re-run.

ALTER TABLE ledger.ledger_accounts
    DROP CONSTRAINT ledger_accounts_account_type_check;

ALTER TABLE ledger.ledger_accounts
    ADD CONSTRAINT ledger_accounts_account_type_check
        CHECK (account_type IN (
            'USER_WALLET',
            'VAULT',
            'SUSU_POT',
            'FEE_REVENUE',
            'PENALTY_REVENUE',
            'PAYSTACK_SETTLEMENT',
            'MOOLRE_SETTLEMENT'
        ));

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
    '00000000-0000-0000-0000-000000000004',
    'MOOLRE_SETTLEMENT',
    'SYSTEM',
    NULL,
    'moolre-settlement-master',
    'ACTIVE',
    'Represents funds received by Moolre on behalf of Stash. DEBIT leg for all Moolre payment-success deposits.',
    NOW()
) ON CONFLICT (id) DO NOTHING;
