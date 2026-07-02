-- ============================================================
-- v0.5-016 : challenge.savings_challenges (Global V31)
--
-- Built against Schema doc §5.1, NOT §4.1 as cited in this issue's AC.
-- §4 is the Transfer/Split chapter — savings_challenges is §5.
--
-- challenge_type is NOT optional detail — v0.5-018's progress consumer
-- dispatches on it to decide whether a deposit should increment
-- progress_amount (SAVE_AMOUNT), progress_count (SAVE_FREQUENCY), or
-- whether the challenge type doesn't respond to deposits at all
-- (STREAK / NO_WITHDRAWAL).
--
-- created_at is an addition beyond Schema doc §5.1 — every other table
-- in this codebase has one; flagged as a doc-update-needed.
-- ============================================================

CREATE SCHEMA IF NOT EXISTS challenge;

CREATE TABLE challenge.savings_challenges (
    id                    UUID          NOT NULL,
    name                  VARCHAR(255)  NOT NULL,
    description           TEXT          NOT NULL,
    challenge_type        VARCHAR(50)   NOT NULL,
    target_amount         BIGINT        NULL,
    target_duration_days  INT           NULL,
    system_owned          BOOLEAN       NOT NULL DEFAULT true,
    creator_user_id       UUID          NULL,
    is_active             BOOLEAN       NOT NULL DEFAULT true,
    badge_id              UUID          NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT savings_challenges_pk PRIMARY KEY (id),

    CONSTRAINT savings_challenges_challenge_type_check
        CHECK (challenge_type IN ('SAVE_AMOUNT', 'SAVE_FREQUENCY', 'STREAK', 'NO_WITHDRAWAL')),

    -- v1.0 has ONLY system_owned=true rows (creator_user_id NULL).
    -- v1.5 user-created challenges will be system_owned=false with a real
    -- creator_user_id. Documenting the invariant now even though it won't
    -- be violated until v1.5 adds the creation endpoint.
    CONSTRAINT savings_challenges_ownership_check
        CHECK (
            (system_owned = true  AND creator_user_id IS NULL)
            OR
            (system_owned = false AND creator_user_id IS NOT NULL)
        )
);

COMMENT ON TABLE challenge.savings_challenges IS
    'Challenge templates (§5.1). All v0.5-seeded rows (v0.5-017) are system_owned=true.';
COMMENT ON COLUMN challenge.savings_challenges.challenge_type IS
    'Drives v0.5-018 progress dispatch: SAVE_AMOUNT→progress_amount, '
    'SAVE_FREQUENCY→progress_count, STREAK/NO_WITHDRAWAL respond to '
    'different signals (not deposit amount alone).';
COMMENT ON COLUMN challenge.savings_challenges.created_at IS
    'Addition beyond §5.1 — every other monolith table has one.';

CREATE INDEX savings_challenges_active_idx
    ON challenge.savings_challenges (is_active)
    WHERE is_active = true;
