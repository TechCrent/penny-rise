-- ============================================================
-- V43 / v0.5-029 : add FROZEN to vault.vaults.status
--
-- A vault beyond the free-tier count limit transitions to FROZEN on
-- subscription downgrade (VaultFreezingService). Funds remain safe —
-- deposits/withdrawals/early-exit are blocked while FROZEN (enforced in
-- v0.5-030), and the vault returns to ACTIVE automatically when the
-- limit is resolved (re-upgrade, or closing other vaults — the exact
-- unfreeze mechanism is out of this issue's scope, same open item
-- flagged against v0.5-031's freezing baseline in v0.4/v0.5 planning).
-- ============================================================

ALTER TABLE vault.vaults
    DROP CONSTRAINT IF EXISTS vaults_status_check;

ALTER TABLE vault.vaults
    ADD CONSTRAINT vaults_status_check
    CHECK (status IN ('ACTIVE', 'LOCKED', 'EARLY_EXIT_PENDING', 'CLOSED', 'FROZEN'));

COMMENT ON COLUMN vault.vaults.status IS
    'FROZEN added in V43 (v0.5-029): set when a vault exceeds the free-tier '
    'count limit on subscription downgrade. Deposits, withdrawals, and '
    'early-exit requests are blocked while FROZEN (see VaultFreezingService, '
    'and the operation guards wired in v0.5-030).';
