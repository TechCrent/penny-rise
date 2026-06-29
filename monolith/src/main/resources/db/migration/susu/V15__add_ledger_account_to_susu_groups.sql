-- ============================================================
-- v0.4-009 : add ledger_account_id to susu.susu_groups
-- The SUSU_POT ledger account is provisioned in the Payments
-- Service when the group is activated and stored here so the
-- contribution service can look it up without a round-trip.
-- Nullable: existing PENDING groups have no ledger account yet.
-- ============================================================

ALTER TABLE susu.susu_groups
    ADD COLUMN ledger_account_id UUID NULL;

COMMENT ON COLUMN susu.susu_groups.ledger_account_id IS
    'Logical FK -> Payments Service ledger account (SUSU_POT type). '
    'Provisioned at group activation and stored here for fast lookup '
    'during contribution transfers. NULL while the group is PENDING.';
