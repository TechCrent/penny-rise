-- Migration: V9__add_unlocked_at_to_vaults.sql
-- Flyway version 9: the vault locations already contain V6 (vault.vaults),
-- V7 (locked_vault_early_exit_requests) and V8 (attempts columns). Versions are
-- GLOBAL across all locations (see db/migration/MIGRATIONS.md) and out-of-order
-- is disabled, so this must be V9.
--
-- Adds unlocked_at to record when a vault's lock conditions were naturally met.
-- NULL for STANDARD vaults and LOCKED vaults not yet unlocked.
-- Set by the auto-unlock worker when conditions are satisfied.

ALTER TABLE vault.vaults
    ADD COLUMN unlocked_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN vault.vaults.unlocked_at IS
    'Timestamp when the vault''s lock conditions were naturally met and the '
    'vault transitioned to freely-withdrawable state. NULL for STANDARD vaults '
    'and LOCKED vaults whose conditions have not yet been met. '
    'Distinct from the early-exit flow — no penalty applies when set.';
