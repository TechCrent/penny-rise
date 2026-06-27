-- Migration: V10__add_momo_to_early_exit_requests.sql
-- Flyway version 10: vault locations already contain V6 (vault.vaults),
-- V7 (locked_vault_early_exit_requests), V8 (attempts columns), and V9
-- (unlocked_at). Versions are GLOBAL across all locations (see
-- db/migration/MIGRATIONS.md) and out-of-order is disabled, so this must
-- be V10.
--
-- Adds destination_momo_number and momo_provider to
-- locked_vault_early_exit_requests, captured at request time.
--
-- The user profile has no dedicated MoMo field (planned for v0.5) — the
-- auto-release worker previously fell back to the user's registered phone
-- number, which could change or be cleared during the 72-hour cool-off,
-- leaving the worker unable to pay out and stuck emitting P0 alerts.
-- Capturing the number on the request row at creation time removes that
-- dependency entirely.

ALTER TABLE vault.locked_vault_early_exit_requests
    ADD COLUMN destination_momo_number VARCHAR(20) NOT NULL DEFAULT '';

ALTER TABLE vault.locked_vault_early_exit_requests
    ADD COLUMN momo_provider VARCHAR(20) NOT NULL DEFAULT '';

ALTER TABLE vault.locked_vault_early_exit_requests
    ALTER COLUMN destination_momo_number DROP DEFAULT,
    ALTER COLUMN momo_provider DROP DEFAULT;

COMMENT ON COLUMN vault.locked_vault_early_exit_requests.destination_momo_number IS
    'MoMo number captured at early-exit request time, used by the '
    'auto-release worker to pay out. Required because user-profile MoMo '
    'is v0.5 — without this, a user clearing their phone number during '
    'the 72-hour cool-off would leave the release worker stuck.';

COMMENT ON COLUMN vault.locked_vault_early_exit_requests.momo_provider IS
    'MoMo network provider captured at request time: mtn, vodafone, or airteltigo.';
