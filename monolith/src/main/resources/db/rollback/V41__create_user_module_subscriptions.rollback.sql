-- ============================================================
-- ROLLBACK for V41__create_user_module_subscriptions.sql
--
-- NOT a Flyway migration — lives outside db/migration/, and outside every
-- path Flyway is configured to scan for user_module (see
-- monolith/src/main/resources/application.yml's flyway.locations), so it
-- can never be accidentally picked up and run as a forward migration.
--
-- Run manually via psql if V41 needs reverting:
--   psql -h <host> -U <user> -d monolith_db -f V41__create_user_module_subscriptions.rollback.sql
--
-- DROP TABLE removes all subscription rows unconditionally, including any
-- written by v0.5-029's application code after this migration landed.
-- Confirm that's actually intended before running this against any
-- environment with real user data.
-- ============================================================

DROP INDEX IF EXISTS user_module.subscriptions_user_ends_at_idx;
DROP TABLE IF EXISTS user_module.subscriptions;
