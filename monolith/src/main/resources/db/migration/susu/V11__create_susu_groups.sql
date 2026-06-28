-- ============================================================
-- v0.4-001 : susu.susu_groups
-- Creates the susu schema and the susu_groups table.
--
-- Schema doc §3.3 reference. One deliberate deviation:
--   target_member_count CHECK is 4-20, not 2-20.
--   The issue spec (v0.4-001, v0.4-005) explicitly requires 4-20.
--   If the Schema doc is later corrected to 4-20, no migration
--   change is needed. If it is corrected to 2-20, add a forward
--   migration to drop and re-add this constraint.
-- ============================================================

CREATE SCHEMA IF NOT EXISTS susu;

CREATE TABLE susu.susu_groups (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    organiser_user_id    UUID          NOT NULL,
    name                 VARCHAR(100)  NOT NULL,
    contribution_amount  BIGINT        NOT NULL,
    frequency            VARCHAR(20)   NOT NULL,
    target_member_count  INT           NOT NULL,
    start_date           DATE          NULL,
    status               VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    current_round_number INT           NULL,
    join_code            VARCHAR(20)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT susu_groups_pk
        PRIMARY KEY (id),

    CONSTRAINT susu_groups_join_code_uk
        UNIQUE (join_code),

    CONSTRAINT susu_groups_status_check
        CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED')),

    CONSTRAINT susu_groups_frequency_check
        CHECK (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY')),

    CONSTRAINT susu_groups_target_member_count_check
        CHECK (target_member_count BETWEEN 4 AND 20),

    CONSTRAINT susu_groups_contribution_amount_positive
        CHECK (contribution_amount > 0),

    CONSTRAINT susu_groups_name_not_blank
        CHECK (length(trim(name)) > 0),

    CONSTRAINT susu_groups_join_code_not_blank
        CHECK (length(trim(join_code)) > 0),

    CONSTRAINT susu_groups_current_round_positive
        CHECK (current_round_number IS NULL OR current_round_number >= 1),

    CONSTRAINT susu_groups_start_date_requires_active
        CHECK (start_date IS NULL OR status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

COMMENT ON TABLE susu.susu_groups IS
    'Top-level entity for a rotating savings (susu) group. '
    'Created in PENDING state; transitions to ACTIVE on organiser activation; '
    'transitions to COMPLETED when all rounds disburse or CANCELLED if abandoned.';

COMMENT ON COLUMN susu.susu_groups.id IS
    'UUIDv4 PK. UUIDv7 preferred but gen_random_uuid() is the Flyway-safe default '
    'until the uuid-ossp or pg_uuidv7 extension is confirmed available.';
COMMENT ON COLUMN susu.susu_groups.organiser_user_id IS
    'Logical FK -> user_module.users.id. Physical FK omitted (cross-schema, '
    'user rows soft-deleted). Validated at the service layer.';
COMMENT ON COLUMN susu.susu_groups.contribution_amount IS
    'Fixed contribution per member per round, in pesewas.';
COMMENT ON COLUMN susu.susu_groups.frequency IS
    'Collection cadence: WEEKLY, BIWEEKLY, or MONTHLY.';
COMMENT ON COLUMN susu.susu_groups.target_member_count IS
    'How many members the group needs before it can be activated. '
    'Between 4 and 20 inclusive (v0.4-005 spec). '
    'Schema doc §3.3 says 2-20; the implementation follows the issue spec.';
COMMENT ON COLUMN susu.susu_groups.start_date IS
    'Calendar date when the first round begins collecting. '
    'NULL while the group is PENDING.';
COMMENT ON COLUMN susu.susu_groups.status IS
    'Lifecycle: PENDING → ACTIVE → COMPLETED / CANCELLED. No backwards transitions.';
COMMENT ON COLUMN susu.susu_groups.current_round_number IS
    'Which round is currently collecting or disbursing. '
    'NULL until activation; 1-based thereafter.';
COMMENT ON COLUMN susu.susu_groups.join_code IS
    'Short, human-readable code the organiser shares with prospective members. '
    '8-character alphanumeric string generated at creation; unique across all groups.';

-- ── Indexes ────────────────────────────────────────────────────────────────

-- join_code unique index is already created by the UNIQUE constraint above.
-- Explicitly named for clarity and consistent with the convention in this codebase.

CREATE INDEX susu_groups_status_idx
    ON susu.susu_groups (status);

COMMENT ON INDEX susu.susu_groups_status_idx IS
    'Supports operational queries filtering by status '
    '(e.g. "all ACTIVE groups", "all PENDING groups waiting for more members").';

CREATE INDEX susu_groups_organiser_idx
    ON susu.susu_groups (organiser_user_id);

COMMENT ON INDEX susu.susu_groups_organiser_idx IS
    'Supports "list groups I organise" queries on the organiser dashboard.';
