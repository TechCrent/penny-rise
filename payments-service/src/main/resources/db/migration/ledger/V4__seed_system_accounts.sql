-- Migration: V4__seed_system_accounts.sql
-- Seeds the PAYSTACK_SETTLEMENT system ledger account.
--
-- This account represents funds held at Paystack (i.e. deposits received
-- by Paystack on our behalf and not yet settled to our bank). It is the
-- DEBIT leg of every charge.success double-entry — money moves from here
-- to the user's USER_WALLET (CREDIT).
--
-- The UUID is fixed (00000000-0000-0000-0000-000000000001) so that
-- application.yml / environment configuration can reference it by value
-- without a lookup query. All environments use the same UUID.
--
-- owner_id = NULL: system accounts have no individual owner.
-- owner_type = 'SYSTEM': distinguishes from 'USER' accounts.
-- account_type = 'PAYSTACK_SETTLEMENT': routing key for charge.success handler.
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
    '00000000-0000-0000-0000-000000000001',
    'PAYSTACK_SETTLEMENT',
    'SYSTEM',
    NULL,
    'paystack-settlement-master',
    'ACTIVE',
    'Represents funds received by Paystack on behalf of Stash. DEBIT leg for all charge.success deposits.',
    NOW()
) ON CONFLICT (id) DO NOTHING;
