-- ============================================================
-- V46 / v0.5-034 : flagged_for_review on susu.susu_groups
--
-- Set by SusuGroupFlaggingListener (new in this issue), which listens for
-- SusuContributionLateEvent where isPenaltyWaived() == true — i.e. a
-- member's late penalty could not be collected (insufficient balance,
-- unresolvable wallet, or the SUSU_POT ledger account itself being
-- unresolvable), per this issue's own AC and confirmed directly against
-- SusuLatePenaltyProcessor's real waive paths. NOT set by the
-- disbursement worker (SusuDisbursementProcessor) — that class tracks
-- expected_pot_amount vs actual_pot_amount for a separate drift-detection
-- concern (SusuPotIntegrityChecker), unrelated to penalty-collection
-- failure.
-- ============================================================

ALTER TABLE susu.susu_groups
    ADD COLUMN flagged_for_review BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN flagged_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN susu.susu_groups.flagged_for_review IS
    'Set by SusuGroupFlaggingListener when a contribution''s late penalty '
    'is waived (SusuContributionLateEvent.penaltyWaived = true) — a real '
    'financial shortfall signal, not a routine late payment. Cleared only '
    'by an explicit admin action (POST .../clear-flag) — never auto-clears.';

CREATE INDEX susu_groups_flagged_idx
    ON susu.susu_groups (flagged_for_review)
    WHERE flagged_for_review = true;
