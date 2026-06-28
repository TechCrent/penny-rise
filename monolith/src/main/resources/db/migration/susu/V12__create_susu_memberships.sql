-- ============================================================
-- v0.4-002 : susu.susu_memberships
-- One row per member per susu group.
--
-- Schema doc §3.4 reference.
--
-- Status enum deviation from issue description:
--   Issue description says: ACTIVE, LEFT, REMOVED
--   Schema doc §3.4 says:   ACTIVE, REMOVED, COMPLETED
--   Building with Schema doc values. 'LEFT' is not a valid status.
--   If 'LEFT' is later decided as the correct term for voluntary exit,
--   a forward migration renaming 'REMOVED' or adding 'LEFT' is required.
--   See tracking item 1.
-- ============================================================

CREATE TABLE susu.susu_memberships (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    susu_group_id     UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    rotation_position INT          NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    joined_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    removed_at        TIMESTAMPTZ  NULL,

    CONSTRAINT susu_memberships_pk
        PRIMARY KEY (id),

    CONSTRAINT susu_memberships_group_fk
        FOREIGN KEY (susu_group_id)
        REFERENCES susu.susu_groups(id)
        ON DELETE CASCADE,

    CONSTRAINT susu_memberships_status_check
        CHECK (status IN ('ACTIVE', 'REMOVED', 'COMPLETED')),

    CONSTRAINT susu_memberships_rotation_position_positive
        CHECK (rotation_position IS NULL OR rotation_position >= 1),

    CONSTRAINT susu_memberships_removed_at_requires_non_active
        CHECK (removed_at IS NULL OR status IN ('REMOVED', 'COMPLETED')),

    CONSTRAINT susu_memberships_group_user_uk
        UNIQUE (susu_group_id, user_id)
);

COMMENT ON TABLE susu.susu_memberships IS
    'One row per member per susu group. '
    'Created when a user joins (status = ACTIVE, rotation_position = NULL). '
    'rotation_position assigned 1..N in join order at group activation. '
    'A member who leaves or is removed transitions to REMOVED; '
    'a member who has received their payout transitions to COMPLETED '
    'at the end of the full cycle.';

COMMENT ON COLUMN susu.susu_memberships.susu_group_id IS
    'Physical FK -> susu.susu_groups.id. CASCADE DELETE ensures memberships '
    'are cleaned up if a group is hard-deleted (rare; groups are usually CANCELLED).';
COMMENT ON COLUMN susu.susu_memberships.user_id IS
    'Logical FK -> user_module.users.id. No physical FK (cross-schema; '
    'users are soft-deleted, not hard-deleted).';
COMMENT ON COLUMN susu.susu_memberships.rotation_position IS
    'Which round this member receives the pot (1-based). '
    'NULL until the organiser activates the group. '
    'Immutable once set — positions never change mid-cycle.';
COMMENT ON COLUMN susu.susu_memberships.status IS
    'ACTIVE: currently participating. '
    'REMOVED: left mid-cycle or removed by organiser. '
    'COMPLETED: full cycle finished; member received their payout.';
COMMENT ON COLUMN susu.susu_memberships.removed_at IS
    'Timestamp when the member was removed or left. '
    'NULL for ACTIVE and COMPLETED members.';

-- ── Indexes ────────────────────────────────────────────────────────────────

-- The UNIQUE constraint on (susu_group_id, user_id) already creates an index.
-- Two additional indexes below.

-- Partial unique index: no two ACTIVE members share a position in the same group.
-- NULL rotation_position is excluded naturally (NULL != NULL in unique indexes).
CREATE UNIQUE INDEX susu_memberships_active_position_uk
    ON susu.susu_memberships (susu_group_id, rotation_position)
    WHERE status = 'ACTIVE';

COMMENT ON INDEX susu.susu_memberships_active_position_uk IS
    'Enforces that no two ACTIVE members can hold the same rotation_position '
    'within a group. NULL positions (pre-activation) are excluded from this '
    'constraint — multiple members can have rotation_position = NULL '
    'simultaneously while the group is PENDING.';

-- Lookup index: "all memberships for this user" (used in list-groups endpoint).
CREATE INDEX susu_memberships_user_idx
    ON susu.susu_memberships (user_id)
    WHERE status = 'ACTIVE';

COMMENT ON INDEX susu.susu_memberships_user_idx IS
    'Partial index on user_id filtered to ACTIVE members. '
    'Supports "list groups I belong to" without scanning REMOVED/COMPLETED rows.';
