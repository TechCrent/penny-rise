-- ============================================================
-- v0.5-017 : wire savings_challenges.badge_id → challenge.badges (Global V36)
-- ============================================================

ALTER TABLE challenge.savings_challenges
    ADD CONSTRAINT savings_challenges_badge_fk
        FOREIGN KEY (badge_id) REFERENCES challenge.badges(id);

COMMENT ON COLUMN challenge.savings_challenges.badge_id IS
    'FK to challenge.badges.id, wired in v0.5-017/V36. NULL = no badge '
    'reward for this challenge.';
