-- ============================================================
-- v0.5-018 : re-scope user_challenges uniqueness to ACTIVE only (Global V37)
--
-- v0.5-016 built UNIQUE(user_id, challenge_id) per its own literal AC/DoD
-- and flagged it would permanently block re-enrollment after
-- ABANDONED/COMPLETED/FAILED. This issue's AC confirms the intent: "user
-- has no existing ACTIVE enrollment" — concurrent double-join is blocked,
-- re-joining after leaving/failing is not.
--
-- Postgres does not support WHERE on a table CONSTRAINT — partial
-- uniqueness requires a UNIQUE INDEX.
-- ============================================================

ALTER TABLE challenge.user_challenges
    DROP CONSTRAINT user_challenges_user_challenge_uk;

CREATE UNIQUE INDEX user_challenges_active_user_challenge_uk
    ON challenge.user_challenges (user_id, challenge_id)
    WHERE status = 'ACTIVE';

COMMENT ON INDEX challenge.user_challenges_active_user_challenge_uk IS
    'Replaces the full UNIQUE from v0.5-016. A user may re-enrol in a '
    'challenge they previously ABANDONED, FAILED, or COMPLETED; they just '
    'cannot have two simultaneous ACTIVE enrollments in the same challenge.';
