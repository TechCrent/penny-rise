-- Migration: V8__add_attempts_to_early_exit_requests.sql
-- Flyway version 8: vault locations already contain V6 (vault.vaults) and
-- V7 (locked_vault_early_exit_requests). Versions are GLOBAL across all
-- locations (see db/migration/MIGRATIONS.md) and out-of-order is disabled,
-- so this must be V8.
--
-- Adds an attempts column to track retry count for the auto-release worker.
-- After 5 failed attempts, the worker emits a P0 alert and stops retrying
-- automatically (ops must intervene).

ALTER TABLE vault.locked_vault_early_exit_requests
    ADD COLUMN attempts INT NOT NULL DEFAULT 0;

ALTER TABLE vault.locked_vault_early_exit_requests
    ADD COLUMN last_attempted_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN vault.locked_vault_early_exit_requests.attempts IS
    'Number of release attempts by the auto-release worker. '
    'Incremented on each attempt (success or failure). '
    'After 5 failed attempts, a P0 alert is emitted and the worker stops retrying.';

COMMENT ON COLUMN vault.locked_vault_early_exit_requests.last_attempted_at IS
    'Timestamp of the most recent release attempt. NULL until first attempt.';
