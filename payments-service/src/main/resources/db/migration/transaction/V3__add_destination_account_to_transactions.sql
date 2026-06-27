-- Migration: V3__add_destination_account_to_transactions.sql
-- Adds destination_ledger_account_id to transaction.transactions.
--
-- Purpose: DepositService validates and resolves the destination ledger
-- account at deposit-initiation time (request.ledgerAccountId() — the
-- USER_WALLET for a direct deposit, or a vault's ledger account for a
-- vault deposit), but never persisted it. ChargeSuccessHandler then had no
-- way to know the destination when the webhook confirmed payment, so it
-- unconditionally resolved the user's USER_WALLET — meaning vault deposits
-- silently credited the wrong account. Storing it on the transaction row
-- (same denormalisation pattern as source_ledger_account_id in V2) lets
-- the webhook handler credit the correct account.
--
-- NULL for WITHDRAWAL and TRANSFER transactions (source/ledger write
-- command determines routing for those). Set for DEPOSIT transactions
-- at creation time.

ALTER TABLE transaction.transactions
    ADD COLUMN destination_ledger_account_id UUID NULL;

COMMENT ON COLUMN transaction.transactions.destination_ledger_account_id IS
    'For deposit transactions: the ledger account that should receive the '
    'credit. Used by ChargeSuccessHandler to route the webhook credit to '
    'the correct account instead of defaulting to USER_WALLET.';
