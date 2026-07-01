-- ============================================================
-- V24 / v0.5-006 : add ADMIN_FORCE_LOGOUT to auth.refresh_tokens.revoked_reason
--
-- Force-logout (admin-initiated) is a new reason a customer refresh token
-- can be revoked, distinct from the existing ADMIN_FORCE placeholder value.
-- ADMIN_FORCE_LOGOUT carries semantics: the actor was an admin and the
-- action was deliberate account session termination; see admin.admin_audit_actions
-- (action_type = USER_FORCE_LOGOUT) for the actor and reason context.
-- ============================================================

ALTER TABLE auth.refresh_tokens
    DROP CONSTRAINT IF EXISTS refresh_tokens_revoked_reason_check;

ALTER TABLE auth.refresh_tokens
    ADD CONSTRAINT refresh_tokens_revoked_reason_check
    CHECK (revoked_reason IS NULL OR revoked_reason IN (
        'USER_LOGOUT',
        'ROTATION',
        'ROTATION_REPLAY',
        'ADMIN_FORCE',
        'EXPIRED',
        'ADMIN_FORCE_LOGOUT'
    ));

COMMENT ON COLUMN auth.refresh_tokens.revoked_reason IS
    'ADMIN_FORCE_LOGOUT added in V24 (v0.5-006): an admin revoked every active '
    'session for this user via POST /api/v1/admin/users/{id}/force-logout. '
    'Does not imply account_status changed — see admin.admin_audit_actions '
    'for the actor and reason context.';
