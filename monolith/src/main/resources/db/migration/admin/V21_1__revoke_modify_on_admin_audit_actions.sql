-- ============================================================
-- v0.5-002 (part 1b) : Append-only enforcement on
-- admin.admin_audit_actions.
--
-- Revokes UPDATE and DELETE from the application's connection role
-- (stash_app), following the identical pattern already in production
-- use for payments_db's ledger.ledger_entries
-- (V4__revoke_modify_on_ledger_entries.sql, per Folder Structure §3.6).
--
-- IMPORTANT: this REVOKE must run in a SEPARATE migration file from
-- the CREATE TABLE statement (V21), not appended to the same file.
-- This matches the convention already established for ledger_entries
-- and outbox_events — keeping CREATE and REVOKE in separate versioned
-- migrations means a future "add a column" migration to this table
-- can run without needing to also restate the REVOKE, and makes the
-- access-control change individually auditable in migration history.
--
-- The REVOKE is conditional: if the stash_app role does not yet exist
-- (e.g. on a freshly-provisioned dev database or in CI against a
-- testcontainer that has no pre-created roles), this migration skips
-- the REVOKE rather than failing. The role should be created by
-- infrastructure/deployment scripts (Terraform, init.sql, etc.), not
-- by Flyway. When stash_app is present, the REVOKE applies immediately.
-- ============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stash_app') THEN
        REVOKE UPDATE, DELETE ON admin.admin_audit_actions FROM stash_app;
    END IF;
END $$;

-- INSERT and SELECT remain granted — the application can still write
-- new audit rows and the admin dashboard can still read them. Only
-- mutation of existing rows is blocked.
