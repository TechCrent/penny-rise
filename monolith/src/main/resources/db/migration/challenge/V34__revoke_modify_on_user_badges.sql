-- ============================================================
-- v0.5-016 : append-only enforcement on challenge.user_badges (Global V34)
--
-- Same conditional-REVOKE pattern as V21_1 (admin.admin_audit_actions):
-- if stash_app doesn't exist yet (fresh dev DB, Testcontainers CI), the
-- REVOKE is skipped rather than failing the migration chain. The role is
-- created by infrastructure/deployment scripts, not by Flyway.
-- ============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'stash_app') THEN
        REVOKE UPDATE, DELETE ON challenge.user_badges FROM stash_app;
    END IF;
END $$;

COMMENT ON TABLE challenge.user_badges IS
    'Award log (§5.3), append-only. UPDATE/DELETE revoked from stash_app '
    'in V34 — INSERT and SELECT only. Same pattern as '
    'V21_1__revoke_modify_on_admin_audit_actions.';
