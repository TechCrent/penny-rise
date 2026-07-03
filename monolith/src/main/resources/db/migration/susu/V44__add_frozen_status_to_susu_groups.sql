-- ============================================================
-- V44 / v0.5-029 : add FROZEN to susu.susu_groups.status
--
-- A susu group beyond the free-tier organiser-count limit transitions to
-- FROZEN on subscription downgrade (SusuFreezingService), same mechanism
-- as vault.vaults' FROZEN status (V43). Contributions are blocked while
-- FROZEN (enforced in v0.5-030).
--
-- Flagged: this is the first status value added to susu_groups since
-- V11 (PENDING/ACTIVE/COMPLETED/CANCELLED). Other susu-module code paths
-- that query or branch on status (disbursement worker, round rollforward,
-- membership listing) have not been individually audited for FROZEN
-- handling as part of this migration — most existing queries are scoped
-- to specific statuses (e.g. 'ACTIVE') and will simply skip FROZEN groups
-- by construction, which is the desired behaviour, but this has not been
-- verified against every consumer.
-- ============================================================

ALTER TABLE susu.susu_groups
    DROP CONSTRAINT IF EXISTS susu_groups_status_check;

ALTER TABLE susu.susu_groups
    ADD CONSTRAINT susu_groups_status_check
    CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'FROZEN'));

COMMENT ON COLUMN susu.susu_groups.status IS
    'FROZEN added in V44 (v0.5-029): set when the organiser exceeds the '
    'free-tier active-organiser-group limit on subscription downgrade. New '
    'contribution prompts are blocked while FROZEN (see SusuFreezingService, '
    'and the operation guard wired in v0.5-030). Existing members and '
    'organiser status are otherwise unaffected.';
