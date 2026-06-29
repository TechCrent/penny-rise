-- ============================================================
-- v0.4-012: Add LEFT status to susu.susu_memberships.
-- LEFT = member voluntarily left (PENDING groups only via user flow;
--        ACTIVE groups via admin dispute path only).
-- REMOVED = organiser/admin-removed mid-cycle.
-- COMPLETED = full cycle finished.
-- The distinction matters for analytics and for the self-service leave
-- endpoint which must only allow voluntary leaving from PENDING groups.

ALTER TABLE susu.susu_memberships
    DROP CONSTRAINT susu_memberships_status_check;

ALTER TABLE susu.susu_memberships
    ADD CONSTRAINT susu_memberships_status_check
        CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED', 'COMPLETED'));

-- removed_at is applicable to both LEFT and REMOVED; constraint already allows it.
-- The removed_at_requires_non_active CHECK still holds — update it to also allow LEFT:
ALTER TABLE susu.susu_memberships
    DROP CONSTRAINT susu_memberships_removed_at_requires_non_active;

ALTER TABLE susu.susu_memberships
    ADD CONSTRAINT susu_memberships_removed_at_requires_non_active
        CHECK (removed_at IS NULL OR status IN ('LEFT', 'REMOVED', 'COMPLETED'));

COMMENT ON COLUMN susu.susu_memberships.status IS
    'ACTIVE: currently participating. '
    'LEFT: voluntarily left (only from PENDING groups via user flow). '
    'REMOVED: removed by organiser (PENDING) or admin (ACTIVE via disputes). '
    'COMPLETED: full cycle finished; member received their payout.';
