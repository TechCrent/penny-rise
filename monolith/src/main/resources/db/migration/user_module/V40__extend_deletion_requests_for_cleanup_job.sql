-- ============================================================
-- V40 / v0.5-019 : extend user_module.deletion_requests for the cleanup job
--
-- Schema doc §1.4's original column list has no attempts column and no FAILED
-- status value. This issue's retry/FAILED mechanics need both.
--
-- attempts: incremented by DeletionCleanupJob on each failed execution attempt.
--           Stays at 0 for STILL_BLOCKED outcomes (those are expected, not errors).
--           When attempts reaches 5 the row flips to FAILED and a P0 alert is logged.
--
-- FAILED status: permanent terminal state requiring manual ops intervention.
--                Added alongside 'PENDING', 'COMPLETED', 'CANCELLED'.
-- ============================================================

ALTER TABLE user_module.deletion_requests
    ADD COLUMN attempts INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN user_module.deletion_requests.attempts IS
    'Cleanup job retry counter (v0.5-019). Incremented on each failed execution '
    'attempt; status flips to FAILED at 5. STILL_BLOCKED outcomes do not '
    'increment this counter — they are expected, not errors.';

ALTER TABLE user_module.deletion_requests
    DROP CONSTRAINT IF EXISTS deletion_requests_status_check;

ALTER TABLE user_module.deletion_requests
    ADD CONSTRAINT deletion_requests_status_check
    CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED', 'FAILED'));
