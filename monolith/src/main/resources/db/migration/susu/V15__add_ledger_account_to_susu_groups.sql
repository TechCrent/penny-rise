-- ============================================================
-- v0.4-008 : Add ledger_account_id to susu.susu_groups
-- Stores the SUSU_POT ledger account provisioned in the Payments
-- Service at group activation. NULL until activation.
-- ============================================================

ALTER TABLE susu.susu_groups
    ADD COLUMN ledger_account_id UUID NULL;

COMMENT ON COLUMN susu.susu_groups.ledger_account_id IS
    'Logical FK -> ledger.ledger_accounts.id in the Payments Service. '
    'Set atomically at group activation when the SUSU_POT account is provisioned. '
    'NULL while the group is PENDING (not yet activated).';
