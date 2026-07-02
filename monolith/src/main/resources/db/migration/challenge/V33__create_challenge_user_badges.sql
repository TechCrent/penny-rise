-- ============================================================
-- v0.5-016 : challenge.user_badges (Global V33)
--
-- Per Schema doc §5.3, NOT §4.3 as cited in this issue's AC.
--
-- Deliberately polymorphic (awarded_for_entity_type + awarded_for_entity_id),
-- NOT a rigid challenge_id FK as the AC requested. Module Boundaries §7.2's
-- worked example awards the SAVER_X10 badge for a user's TENTH DEPOSIT
-- MILESTONE with no savings_challenges row involved — awarded_for_entity_type
-- = MILESTONE. A rigid challenge_id FK would make that use case impossible
-- without a later schema migration.
--
-- No FK on awarded_for_entity_id for the same reason as
-- admin.disputes.related_entity_id (v0.5-002): the referenced entity may
-- live in a different schema or may not exist in any schema at all (MANUAL).
-- ============================================================

CREATE TABLE challenge.user_badges (
    id                       UUID          NOT NULL,
    user_id                  UUID          NOT NULL,
    badge_code               VARCHAR(100)  NOT NULL,
    badge_name               VARCHAR(255)  NOT NULL,
    awarded_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    awarded_for_entity_type  VARCHAR(50)   NOT NULL,
    awarded_for_entity_id    UUID          NULL,

    CONSTRAINT user_badges_pk PRIMARY KEY (id),

    CONSTRAINT user_badges_entity_type_check
        CHECK (awarded_for_entity_type IN ('CHALLENGE', 'MILESTONE', 'MANUAL'))
);

COMMENT ON TABLE challenge.user_badges IS
    'Award log (§5.3). Append-only — UPDATE/DELETE revoked from stash_app in V34.';
COMMENT ON COLUMN challenge.user_badges.awarded_for_entity_type IS
    'CHALLENGE: awarded_for_entity_id → challenge.user_challenges.id. '
    'MILESTONE: entity_id is context-specific or NULL (§7.2 SAVER_X10 example). '
    'MANUAL: admin-issued; entity_id may be NULL.';

CREATE INDEX user_badges_user_idx
    ON challenge.user_badges (user_id, awarded_at DESC);
