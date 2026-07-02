-- ============================================================
-- V39 / v0.5-019 : add ACCOUNT_DELETED to auth.refresh_tokens.revoked_reason
--
-- Same DROP/ADD pattern as V24 (ADMIN_FORCE_LOGOUT). The full current value
-- list from V24 is preserved exactly; ACCOUNT_DELETED is the only addition.
-- Revocation via ACCOUNT_DELETED is written by DeletionExecutionService as
-- the second step of the cleanup saga (after soft-deleting the user row).
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
        'ADMIN_FORCE_LOGOUT',
        'ACCOUNT_DELETED'
    ));

COMMENT ON COLUMN auth.refresh_tokens.revoked_reason IS
    'ACCOUNT_DELETED added in V39 (v0.5-019): all active sessions revoked as '
    'part of the 30-day cool-off cleanup saga. See DeletionExecutionService '
    'for the full multi-step sequence.';
