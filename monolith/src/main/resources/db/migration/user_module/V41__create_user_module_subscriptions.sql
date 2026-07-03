-- ============================================================
-- v0.5-028 : user_module.subscriptions
--
-- Tracks the subscription tier for every user. Exactly one row per user
-- at any time, enforced by a plain UNIQUE(user_id) constraint, per the
-- AC's own explicit and repeated wording ("UNIQUE constraint on user_id
-- — one active row per user"). Renewal/cancellation/upgrade (v0.5-029,
-- not built here) update this single row in place — the AC never asks
-- for tier-change history, so this migration doesn't invent an is_active/
-- partial-index design for a requirement that isn't there. A plain
-- table-level UNIQUE constraint has no relation to Postgres's rule
-- against non-immutable expressions in partial-index predicates (that
-- limitation only applies if you need "WHERE ends_at > now()" or
-- similar, which a single-row-per-user design never needs).
--
-- users.subscription_tier (V1) remains the denormalised read cache, kept
-- in sync by v0.5-029's subscription service — this migration only
-- creates the authoritative table, it does not touch that column.
-- ============================================================

CREATE TABLE user_module.subscriptions (
    id                              UUID          NOT NULL,
    user_id                         UUID          NOT NULL,
    tier                            VARCHAR(50)   NOT NULL,
    started_at                      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ends_at                         TIMESTAMPTZ   NULL,
    source                          VARCHAR(50)   NOT NULL,
    external_subscription_reference VARCHAR(255)  NULL,
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT subscriptions_pk PRIMARY KEY (id),

    CONSTRAINT subscriptions_user_fk
        FOREIGN KEY (user_id)
        REFERENCES user_module.users(id),

    CONSTRAINT subscriptions_user_uk
        UNIQUE (user_id),

    CONSTRAINT subscriptions_tier_check
        CHECK (tier IN ('FREE', 'PREMIUM')),

    CONSTRAINT subscriptions_source_check
        CHECK (source IN ('PAYSTACK_SUB', 'SYSTEM')),

    -- Enforces the AC's "ends_at (NULL for FREE)" as a real invariant,
    -- not just a documentation note.
    CONSTRAINT subscriptions_tier_ends_at_check
        CHECK (tier <> 'FREE' OR ends_at IS NULL)
);

COMMENT ON TABLE user_module.subscriptions IS
    'Authoritative source for a user''s current subscription tier. Exactly '
    'one row per user (subscriptions_user_uk) — renewal, cancellation, and '
    'tier changes update this row in place via v0.5-029''s subscription '
    'service. users.subscription_tier (V1) is a denormalised read cache '
    'kept in sync by that same service, not by this migration.';
COMMENT ON COLUMN user_module.subscriptions.external_subscription_reference IS
    'Paystack subscription code. NULL for SYSTEM-sourced FREE rows '
    '(everyone starts here); populated when a user upgrades via Paystack.';

-- Per the AC's literal request. Since user_id is already unique, this
-- mainly supports "which PREMIUM users have an ends_at coming up soon"
-- style scans, not single-user lookups (those just use the FK/unique index).
CREATE INDEX subscriptions_user_ends_at_idx
    ON user_module.subscriptions (user_id, ends_at);

-- ── Backfill: every existing user gets exactly one FREE row ──────────────
--
-- gen_random_uuid() (UUID v4), not application-generated UUID v7, for this
-- one-time bulk backfill — a raw SQL migration has no access to the shared
-- uuid-v7 Java library, and v7's time-ordering benefit doesn't apply when
-- every row is inserted in the same instant anyway. Precedent: V18's
-- beta_allowlist.id uses the same function. All rows created going forward
-- through v0.5-029's application code should generate true UUID v7 ids,
-- matching users.id's own convention.

-- No NOT EXISTS guard needed: subscriptions is created fresh by this same
-- migration, so it cannot already hold a row for any user at this point.
INSERT INTO user_module.subscriptions
    (id, user_id, tier, started_at, ends_at, source, created_at, updated_at)
SELECT
    gen_random_uuid(),
    u.id,
    'FREE',
    u.created_at,
    NULL,
    'SYSTEM',
    now(),
    now()
FROM user_module.users u;
