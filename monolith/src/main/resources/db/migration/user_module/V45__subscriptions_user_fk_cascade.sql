-- ============================================================
-- V45 / v0.5-029 : subscriptions_user_fk -> ON DELETE CASCADE
--
-- V41 created subscriptions_user_fk with no ON DELETE clause (Postgres
-- default: NO ACTION), matching DeletionRequestsConstraintsTest's explicit
-- expectation that hard-deleting a user with a subscription row fails —
-- that was written before SignupService (this issue) started creating a
-- subscription row for every new user.
--
-- Once every user always has exactly one subscription row (this issue),
-- NO ACTION broke every existing test that creates a user via the real
-- signup flow and then hard-deletes via userRepository.deleteAll() in
-- cleanup (LogoutServiceTest, ResetPasswordServiceTest,
-- TokenRefreshServiceTest, UserProfileServiceTest, LoginServiceTest, and
-- others) — CI caught this on the first push.
--
-- CASCADE matches the established convention for auxiliary per-user
-- records in this schema (refresh_tokens, notifications,
-- email_verification_tokens, notification_device_tokens all use
-- ON DELETE CASCADE) as opposed to significant, audit-worthy business
-- records that intentionally block deletion (deletion_requests, vaults
-- use ON DELETE RESTRICT). A subscription row is the former, not the
-- latter — there's no scenario where a dangling subscription row for a
-- nonexistent user is desirable, and the real production account-deletion
-- flow is soft-delete only (users.deleted_at), so this CASCADE only ever
-- fires in test cleanup or a genuinely exceptional hard-delete.
-- ============================================================

ALTER TABLE user_module.subscriptions
    DROP CONSTRAINT subscriptions_user_fk;

ALTER TABLE user_module.subscriptions
    ADD CONSTRAINT subscriptions_user_fk
        FOREIGN KEY (user_id)
        REFERENCES user_module.users(id)
        ON DELETE CASCADE;
