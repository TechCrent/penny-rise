-- ============================================================
-- V42 / v0.5-029 : add SUBSCRIPTION_TIER_CHANGED to auth.refresh_tokens.revoked_reason
--
-- Same DROP/ADD pattern as V24 (ADMIN_FORCE_LOGOUT) and V39 (ACCOUNT_DELETED).
-- The full current value list from V39 is preserved exactly;
-- SUBSCRIPTION_TIER_CHANGED is the only addition.
--
-- subscription_tier is a JWT access-token claim (JwtTokenService). Without
-- revoking active sessions on upgrade/downgrade, an already-issued access
-- token keeps claiming the OLD tier for up to its remaining 15-minute
-- lifetime. SubscriptionService revokes all active sessions with this
-- reason on every successful upgrade or downgrade commit.
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
        'ACCOUNT_DELETED',
        'SUBSCRIPTION_TIER_CHANGED'
    ));

COMMENT ON COLUMN auth.refresh_tokens.revoked_reason IS
    'SUBSCRIPTION_TIER_CHANGED added in V42 (v0.5-029): all active sessions '
    'revoked on subscription upgrade/downgrade so the subscription_tier JWT '
    'claim refreshes on next login rather than serving a stale tier for up '
    'to 15 minutes. See SubscriptionService.';
