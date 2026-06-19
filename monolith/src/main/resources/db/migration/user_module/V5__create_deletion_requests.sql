-- Migration: V5__create_deletion_requests.sql
-- Flyway version 5: V4 is password_reset_tokens (v0.2-005). See MIGRATIONS.md.
-- Creates user_module.deletion_requests per Schema doc §1.4.
--
-- Design decisions:
--   - No ON DELETE CASCADE on the user_id FK. Unlike tokens (which are
--     ephemeral session state), a deletion request is an auditable record
--     of the user's intent. If the user row is hard-deleted before the
--     request is processed, we want the deletion_request row to remain
--     as a historical record. Using RESTRICT means any hard-delete of a
--     user with a PENDING deletion request requires the caller to first
--     close the request — intentional friction.
--   - blockers_at_submission is JSONB because the blocker shape varies:
--     it may contain open susu group IDs, locked vault IDs, or outstanding
--     split IDs depending on which milestones are complete. A flexible
--     JSONB column avoids repeated schema changes as new blocker types
--     are introduced in later milestones.
--   - status CHECK constraint: varchar + check is preferred over Postgres
--     ENUM for the same reason as other status columns (no ALTER TYPE lock).
--   - Partial index on (scheduled_completion_at) WHERE status = 'PENDING':
--     the v0.5 cleanup job runs daily and selects:
--       WHERE status = 'PENDING' AND scheduled_completion_at <= NOW()
--     Without this partial index that query would scan all rows.
--     With it, only PENDING rows are indexed — typically a small fraction
--     of the table, since most requests will be COMPLETED or CANCELLED.
--   - (user_id) supporting index: the 'my deletion request' screen queries
--     by user_id; without this index that query would scan the table.
--
-- Rollback strategy: forward-only.

CREATE TABLE user_module.deletion_requests (

    id                          UUID            NOT NULL,
    user_id                     UUID            NOT NULL,
    status                      VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    blockers_at_submission      JSONB           NULL,
    submitted_at                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    scheduled_completion_at     TIMESTAMPTZ     NOT NULL,  -- submitted_at + 30 days, set by application
    completed_at                TIMESTAMPTZ     NULL,
    cancelled_at                TIMESTAMPTZ     NULL,

    CONSTRAINT deletion_requests_pkey
        PRIMARY KEY (id),

    -- RESTRICT: do not allow hard-deleting a user who has a pending deletion
    -- request. The caller must close the request first.
    CONSTRAINT deletion_requests_user_fk
        FOREIGN KEY (user_id)
        REFERENCES user_module.users (id)
        ON DELETE RESTRICT,

    CONSTRAINT deletion_requests_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),

    -- A user should have at most one PENDING request at a time.
    -- Enforced at the application layer in v0.2-019; this partial unique
    -- index makes the constraint database-level for safety.
    CONSTRAINT deletion_requests_one_pending_per_user
        EXCLUDE USING btree (user_id WITH =) WHERE (status = 'PENDING')

);

-- Partial index for the v0.5 daily cleanup job.
-- Query: WHERE status = 'PENDING' AND scheduled_completion_at <= NOW()
-- Only PENDING rows are indexed — keeps the index small as the table grows.
CREATE INDEX deletion_requests_cleanup_job_idx
    ON user_module.deletion_requests (scheduled_completion_at)
    WHERE status = 'PENDING';

-- Supporting index for 'my deletion request' screen.
-- Query: WHERE user_id = :uid ORDER BY submitted_at DESC
CREATE INDEX deletion_requests_user_id_idx
    ON user_module.deletion_requests (user_id);

COMMENT ON TABLE user_module.deletion_requests IS
    'One row per account deletion request. 30-day cool-off before execution. '
    'Actual cleanup job ships in v0.5. Schema doc §1.4.';

COMMENT ON COLUMN user_module.deletion_requests.blockers_at_submission IS
    'JSON snapshot of active blockers at request time: open susu groups, '
    'locked vaults, outstanding splits. Empty array {} in v0.2 (no financial '
    'features yet); populated by later milestones. Retained for ops audit trail.';

COMMENT ON COLUMN user_module.deletion_requests.scheduled_completion_at IS
    'submitted_at + 30 days. Set by the application. The v0.5 cleanup job '
    'selects rows where this timestamp has passed and status = PENDING.';

COMMENT ON COLUMN user_module.deletion_requests.cancelled_at IS
    'Set when the user cancels during the 30-day cool-off. '
    'Status moves to CANCELLED; the user resumes normal account usage.';
