-- Migration: V4__add_counterparty_user_id_index.sql
-- Adds the index findHistoryForUser (TransactionRepository) actually needs
-- on the other side of its WHERE clause.
--
-- That query filters on
--   (t.initiating_user_id = :userId OR t.counterparty_user_id = :userId)
-- ORDER BY t.created_at DESC. transactions_initiating_user_id_created_at_idx
-- (V1) covers the first half, but counterparty_user_id had no index at all.
-- Postgres can only satisfy an OR across two columns with a BitmapOr of two
-- index scans if BOTH sides have one — without this index it falls back to
-- a full sequential scan of transaction.transactions for every single
-- history request, from every user, regardless of how narrow the actual
-- result set is. Harmless on a near-empty dev table; it's exactly the shape
-- of bug that only shows up once real data accumulates (reported as
-- "transaction history takes too long to load").

CREATE INDEX transactions_counterparty_user_id_created_at_idx
    ON transaction.transactions (counterparty_user_id, created_at DESC)
    WHERE counterparty_user_id IS NOT NULL;
