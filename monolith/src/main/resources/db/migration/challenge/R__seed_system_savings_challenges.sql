-- ============================================================
-- v0.5-017 : seed system-owned challenges + their badges
--
-- REPEATABLE migration (R__, not V__) — per this issue's own idempotency
-- AC: "re-running it on a database that already has the seed data does not
-- create duplicates." R__ migrations re-apply whenever their checksum
-- changes (e.g. a product edit to copy or a target amount), UPSERTing
-- rather than duplicating.
--
-- Fixed UUIDs are arbitrary but STABLE — do not regenerate them once this
-- has shipped to any real environment; doing so orphans existing
-- user_challenges rows that reference the old challenge_id values.
--
-- ALL CONTENT BELOW IS PROPOSED, NOT DERIVED FROM AN EXISTING CATALOGUE.
-- Overall doc §13.9 has one illustrative example ("save GHS 5 per day for
-- 30 days"), not a launch list. NEEDS PRODUCT SIGN-OFF before this is
-- treated as "the" launch catalogue.
--
-- badge asset_name values also NEED cross-referencing against the mobile
-- app's asset pipeline before shipping — no asset manifest was available
-- here. NO_BREAK_30D is the one badge_code that IS textually confirmed
-- (Schema doc §5.3 cites it verbatim); the others are proposed.
-- ============================================================

-- ── Badges ───────────────────────────────────────────────────────────────

INSERT INTO challenge.badges (id, badge_code, badge_name, asset_name)
VALUES
    ('a1000000-0000-4000-8000-000000000001', 'STARTER_SAVER',    'Starter Saver',    'badge_starter_saver'),
    ('a1000000-0000-4000-8000-000000000002', 'CONSISTENT_SAVER', 'Consistent Saver', 'badge_consistent_saver'),
    ('a1000000-0000-4000-8000-000000000003', 'DEDICATED_SAVER',  'Dedicated Saver',  'badge_dedicated_saver'),
    -- NO_BREAK_30D: the one badge_code confirmed in Schema doc §5.3.
    -- badge_name and asset_name are still proposed.
    ('a1000000-0000-4000-8000-000000000004', 'NO_BREAK_30D',     'No-Break 30',      'badge_no_break_30')
ON CONFLICT (id) DO UPDATE SET
    badge_code = EXCLUDED.badge_code,
    badge_name = EXCLUDED.badge_name,
    asset_name = EXCLUDED.asset_name;

-- ── Challenges ───────────────────────────────────────────────────────────

-- Archetype 1: short / starter (7 days) — per this issue's AC.
INSERT INTO challenge.savings_challenges
    (id, name, description, challenge_type, target_amount, target_duration_days,
     system_owned, creator_user_id, is_active, badge_id)
VALUES
    ('b2000000-0000-4000-8000-000000000001',
     'Save GHS 50 in 7 Days',
     'A quick starter challenge — save GHS 50 across any vault within a week.',
     'SAVE_AMOUNT', 5000, 7,
     true, NULL, true,
     'a1000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO UPDATE SET
    name                 = EXCLUDED.name,
    description          = EXCLUDED.description,
    challenge_type       = EXCLUDED.challenge_type,
    target_amount        = EXCLUDED.target_amount,
    target_duration_days = EXCLUDED.target_duration_days,
    is_active            = EXCLUDED.is_active,
    badge_id             = EXCLUDED.badge_id;

-- Archetype 2: medium (30 days) — per this issue's AC.
INSERT INTO challenge.savings_challenges
    (id, name, description, challenge_type, target_amount, target_duration_days,
     system_owned, creator_user_id, is_active, badge_id)
VALUES
    ('b2000000-0000-4000-8000-000000000002',
     'Save GHS 200 in 30 Days',
     'Build a saving habit — GHS 200 over a month.',
     'SAVE_AMOUNT', 20000, 30,
     true, NULL, true,
     'a1000000-0000-4000-8000-000000000002')
ON CONFLICT (id) DO UPDATE SET
    name                 = EXCLUDED.name,
    description          = EXCLUDED.description,
    challenge_type       = EXCLUDED.challenge_type,
    target_amount        = EXCLUDED.target_amount,
    target_duration_days = EXCLUDED.target_duration_days,
    is_active            = EXCLUDED.is_active,
    badge_id             = EXCLUDED.badge_id;

-- Archetype 3: long (90 days) — per this issue's AC.
INSERT INTO challenge.savings_challenges
    (id, name, description, challenge_type, target_amount, target_duration_days,
     system_owned, creator_user_id, is_active, badge_id)
VALUES
    ('b2000000-0000-4000-8000-000000000003',
     'Save GHS 1000 in 90 Days',
     'The long game — GHS 1000 saved over three months.',
     'SAVE_AMOUNT', 100000, 90,
     true, NULL, true,
     'a1000000-0000-4000-8000-000000000003')
ON CONFLICT (id) DO UPDATE SET
    name                 = EXCLUDED.name,
    description          = EXCLUDED.description,
    challenge_type       = EXCLUDED.challenge_type,
    target_amount        = EXCLUDED.target_amount,
    target_duration_days = EXCLUDED.target_duration_days,
    is_active            = EXCLUDED.is_active,
    badge_id             = EXCLUDED.badge_id;

-- Archetype 4: no-withdrawal streak (30 days) — named in Issue Plan v5's
-- original v0.5-017 text. The one challenge whose badge_code is textually
-- confirmed (NO_BREAK_30D, Schema doc §5.3).
INSERT INTO challenge.savings_challenges
    (id, name, description, challenge_type, target_amount, target_duration_days,
     system_owned, creator_user_id, is_active, badge_id)
VALUES
    ('b2000000-0000-4000-8000-000000000004',
     'No-Withdrawal Streak: 30 Days',
     'Go 30 days without a single withdrawal from any vault.',
     'NO_WITHDRAWAL', NULL, 30,
     true, NULL, true,
     'a1000000-0000-4000-8000-000000000004')
ON CONFLICT (id) DO UPDATE SET
    name                 = EXCLUDED.name,
    description          = EXCLUDED.description,
    challenge_type       = EXCLUDED.challenge_type,
    target_amount        = EXCLUDED.target_amount,
    target_duration_days = EXCLUDED.target_duration_days,
    is_active            = EXCLUDED.is_active,
    badge_id             = EXCLUDED.badge_id;
