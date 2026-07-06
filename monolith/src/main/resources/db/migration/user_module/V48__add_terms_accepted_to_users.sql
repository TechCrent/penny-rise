-- ============================================================
-- V48 : terms_accepted_at on user_module.users
--
-- Gap-analysis finding: no ToS/Privacy Policy consent capture existed
-- anywhere at signup. Nullable because existing rows predate consent
-- capture — they stay NULL rather than being backfilled with a fabricated
-- timestamp. New signups always set this via SignupService.
-- ============================================================

ALTER TABLE user_module.users
    ADD COLUMN terms_accepted_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN user_module.users.terms_accepted_at IS
    'Set at signup when the user accepts the Terms of Service and Privacy '
    'Policy. NULL for accounts created before this column existed.';
