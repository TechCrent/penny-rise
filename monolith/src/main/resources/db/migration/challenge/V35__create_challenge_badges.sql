-- ============================================================
-- v0.5-017 : challenge.badges — closing a schema gap (Global V35)
--
-- savings_challenges.badge_id (§5.1) was an unresolved UUID with nothing
-- to reference — no badges catalogue existed anywhere in the schema, and
-- user_badges (§5.3) awards by badge_code/badge_name strings with no
-- shared lookup. This table provides the single source of truth so
-- v0.5-018 can translate a challenge's badge_id into the badge_code/name
-- it writes to user_badges on completion.
--
-- Schema doc §5.x does not include this table — flagged as a doc-update-
-- needed. This migration is NOT in the AC; it closes the gap this issue
-- itself exposes by requiring badge_id verification.
--
-- asset_name: the field this issue's AC cares about matching against the
-- mobile app's asset pipeline. Kept separate from badge_code so a display
-- rename does not require renaming the asset file and vice versa. The
-- actual values NEED cross-referencing against the mobile repo's asset
-- manifest — that is a manual step, not done here.
-- ============================================================

CREATE TABLE challenge.badges (
    id          UUID          NOT NULL,
    badge_code  VARCHAR(100)  NOT NULL,
    badge_name  VARCHAR(255)  NOT NULL,
    asset_name  VARCHAR(255)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT badges_pk PRIMARY KEY (id),
    CONSTRAINT badges_badge_code_uk UNIQUE (badge_code)
);

COMMENT ON TABLE challenge.badges IS
    'Badge catalogue. Not in Schema doc §5.x — added in v0.5-017 to close '
    'the gap between savings_challenges.badge_id (a bare UUID in §5.1) and '
    'user_badges (awards by badge_code/badge_name in §5.3). Schema doc '
    'update needed.';
COMMENT ON COLUMN challenge.badges.asset_name IS
    'Must match the badge image asset filename in the mobile asset pipeline. '
    'VALUES HERE ARE NOT VERIFIED — no asset manifest was available. '
    'Confirm against mobile/assets/badges/ before shipping.';
