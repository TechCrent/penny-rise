-- Migration: V2__add_source_account_to_transactions.sql
-- Adds source_ledger_account_id to transaction.transactions.
--
-- Purpose: the transfer.failed webhook handler needs to know which ledger
-- account to credit when reversing a failed withdrawal reservation. Without
-- this column the handler would need a separate join through ledger_entries,
-- which is slower and more fragile. Storing it on the transaction row is an
-- intentional denormalisation for fast, reliable reversal.
--
-- NULL for DEPOSIT transactions (no source account — funds come in).
-- Set for WITHDRAWAL transactions at creation time.

ALTER TABLE transaction.transactions
    ADD COLUMN source_ledger_account_id UUID NULL;

COMMENT ON COLUMN transaction.transactions.source_ledger_account_id IS
    'For withdrawal transactions: the ledger account debited. '
    'Used by the transfer.failed webhook handler to reverse the entries.';
