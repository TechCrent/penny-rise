-- ============================================================
-- v0.5-016 : challenge.user_challenges (Global V32)
--
-- Per Schema doc §5.2, NOT §4.2 as cited in this issue's AC.
--
-- user_id is a LOGICAL reference — no physical FK to user_module.users.
-- Module Boundaries §7.2's worked example states this explicitly for the
-- challenge module: "The user_id column there is a UUID — a logical
-- reference to user_module.users. Challenge does not JOIN against users;
-- it trusts it."
-- challenge_id DOES get a physical FK — same schema/module, no module
-- boundary crossed, FK is appropriate.
--
-- progress_count and ends_at are present per §5.2 (omitted from this
-- issue's AC but required by v0.5-018's streak-completion test scenario).
-- status includes FAILED (omitted from the AC) per §5.2's own description
-- of ends_at: "failure if ACTIVE past this."
--
-- NOTE on the UNIQUE constraint: the AC says "cannot enrol the same user
-- in the same challenge twice simultaneously" — built literally as
-- UNIQUE(user_id, challenge_id), which PERMANENTLY blocks re-enrollment
-- even after ABANDONED/FAILED. Flagged: if re-enrollment after
-- completion/failure is wanted, the constraint needs to be a partial unique
-- index (WHERE status = 'ACTIVE') instead. Confirm before v0.5-018.
-- ============================================================

CREATE TABLE challenge.user_challenges (
    id               UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    challenge_id     UUID         NOT NULL,
    status           VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    progress_amount  BIGINT       NOT NULL DEFAULT 0,
    progress_count   INT          NOT NULL DEFAULT 0,
    started_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ends_at          TIMESTAMPTZ  NULL,
    completed_at     TIMESTAMPTZ  NULL,

    CONSTRAINT user_challenges_pk PRIMARY KEY (id),

    CONSTRAINT user_challenges_challenge_fk
        FOREIGN KEY (challenge_id)
        REFERENCES challenge.savings_challenges(id),

    CONSTRAINT user_challenges_status_check
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'ABANDONED')),

    CONSTRAINT user_challenges_user_challenge_uk
        UNIQUE (user_id, challenge_id)
);

COMMENT ON TABLE challenge.user_challenges IS
    'Per Schema doc §5.2. status includes FAILED (omitted from AC enum '
    'list) — §5.2 describes ends_at as triggering failure for ACTIVE rows '
    'past their deadline.';
COMMENT ON COLUMN challenge.user_challenges.progress_count IS
    'For SAVE_FREQUENCY and STREAK challenge_types — omitted from AC.';
COMMENT ON COLUMN challenge.user_challenges.ends_at IS
    'Deadline. A background job transitions ACTIVE → FAILED past this. '
    'Omitted from AC but required for the streak-failure path.';

CREATE INDEX user_challenges_user_status_idx
    ON challenge.user_challenges (user_id, status);
