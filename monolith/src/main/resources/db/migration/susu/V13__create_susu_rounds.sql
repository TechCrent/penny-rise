-- ============================================================
-- v0.4-003 : susu.susu_rounds
-- One round per member per susu group, generated eagerly at
-- group activation.
--
-- Schema doc §3.5 reference. One deviation, same pattern as V11/V12:
--   Issue #99 acceptance criteria says "due_date" and "disbursement_date".
--   Schema doc §3.5 uses "scheduled_collection_at" and "disbursed_at".
--   Building with Schema doc column names (canonical), consistent with
--   how V11 (target_member_count) and V12 (status enum) resolved the
--   same kind of issue-vs-schema-doc conflict.
--
-- status enum (PENDING/COLLECTING/DISBURSING/DISBURSED/COMPLETED/SKIPPED)
-- is taken directly from issue #99's acceptance criteria — no deviation.
-- ============================================================

CREATE TABLE susu.susu_rounds (
    id                           UUID         NOT NULL DEFAULT gen_random_uuid(),
    susu_group_id                UUID         NOT NULL,
    round_number                 INT          NOT NULL,
    recipient_user_id            UUID         NULL,
    status                       VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    scheduled_collection_at      TIMESTAMPTZ  NULL,
    expected_pot_amount          BIGINT       NULL,
    actual_pot_amount            BIGINT       NULL,
    disbursed_at                 TIMESTAMPTZ  NULL,
    disbursement_transaction_id  UUID         NULL,

    CONSTRAINT susu_rounds_pk
        PRIMARY KEY (id),

    CONSTRAINT susu_rounds_group_fk
        FOREIGN KEY (susu_group_id)
        REFERENCES susu.susu_groups(id)
        ON DELETE CASCADE,

    CONSTRAINT susu_rounds_status_check
        CHECK (status IN (
            'PENDING', 'COLLECTING', 'DISBURSING',
            'DISBURSED', 'COMPLETED', 'SKIPPED'
        )),

    CONSTRAINT susu_rounds_round_number_positive
        CHECK (round_number >= 1),

    CONSTRAINT susu_rounds_expected_pot_positive
        CHECK (expected_pot_amount IS NULL OR expected_pot_amount > 0),

    CONSTRAINT susu_rounds_actual_pot_non_negative
        CHECK (actual_pot_amount IS NULL OR actual_pot_amount >= 0),

    CONSTRAINT susu_rounds_disbursed_at_requires_terminal
        CHECK (
            disbursed_at IS NULL
            OR status IN ('DISBURSING', 'DISBURSED', 'COMPLETED')
        ),

    CONSTRAINT susu_rounds_disbursement_txn_requires_terminal
        CHECK (
            disbursement_transaction_id IS NULL
            OR status IN ('DISBURSING', 'DISBURSED', 'COMPLETED')
        )
);

COMMENT ON TABLE susu.susu_rounds IS
    'One row per member per susu group. Rows are inserted eagerly at group '
    'activation — N rows for an N-member group, numbered 1..N. '
    'The disbursement worker advances status through the lifecycle. '
    'Status progression: PENDING -> COLLECTING -> DISBURSING -> DISBURSED -> COMPLETED. '
    'SKIPPED is used when the round recipient has been removed from the group.';

COMMENT ON COLUMN susu.susu_rounds.round_number IS
    '1-based position in the group cycle. Round 1 disburses first. '
    'Unique within a group among non-SKIPPED rounds — enforced by the '
    'partial unique index below.';
COMMENT ON COLUMN susu.susu_rounds.recipient_user_id IS
    'The member who receives this round''s pot. '
    'Set at activation to the member at the corresponding rotation_position. '
    'Nullable per issue spec; in practice always set at INSERT time during activation. '
    'Logical FK -> user_module.users.id (no physical FK — cross-schema).';
COMMENT ON COLUMN susu.susu_rounds.status IS
    'PENDING (not yet collecting), '
    'COLLECTING (contributions being gathered), '
    'DISBURSING (payout transfer in progress — prevents duplicate disbursement), '
    'DISBURSED (transfer landed), '
    'COMPLETED (round fully settled), '
    'SKIPPED (recipient removed; pot distributed differently or forfeited).';
COMMENT ON COLUMN susu.susu_rounds.scheduled_collection_at IS
    'Timestamp when contributions for this round are due. '
    'Derived from the group start_date + (round_number - 1) x frequency interval. '
    'Set at activation.';
COMMENT ON COLUMN susu.susu_rounds.expected_pot_amount IS
    'contribution_amount x active_member_count at round creation time. '
    'In pesewas. May differ from actual_pot_amount if any member is late or missed.';
COMMENT ON COLUMN susu.susu_rounds.actual_pot_amount IS
    'Total actually collected. Set when the round closes for collection '
    'and transitions to DISBURSING. In pesewas.';
COMMENT ON COLUMN susu.susu_rounds.disbursed_at IS
    'Timestamp when the payout transfer to the recipient landed. '
    'NULL until the disbursement worker confirms completion.';
COMMENT ON COLUMN susu.susu_rounds.disbursement_transaction_id IS
    'Logical FK -> the Payments Service transaction record for the pot disbursement. '
    'No physical FK (cross-service). Set when disbursement completes.';

-- ── Indexes ────────────────────────────────────────────────────────────────

-- Partial unique index per issue #99 DoD: round_number unique within a group
-- among non-SKIPPED rounds. SKIPPED excluded so a substitute-member mechanism
-- could re-use the slot in future without needing a migration — same pattern
-- as susu_memberships_active_position_uk (V12) excluding non-ACTIVE members.
CREATE UNIQUE INDEX susu_rounds_group_round_uk
    ON susu.susu_rounds (susu_group_id, round_number)
    WHERE status != 'SKIPPED';

COMMENT ON INDEX susu.susu_rounds_group_round_uk IS
    'Enforces that round_number is unique within a group among non-SKIPPED '
    'rounds. The activation service inserts N rounds at once; a bug that '
    'generates duplicate round numbers would be caught here before committing.';

-- Status index: disbursement worker queries for DISBURSING rounds.
CREATE INDEX susu_rounds_status_idx
    ON susu.susu_rounds (status);

COMMENT ON INDEX susu.susu_rounds_status_idx IS
    'Supports the disbursement worker (SELECT ... WHERE status = ''DISBURSING'') '
    'and the contribution collector (SELECT ... WHERE status = ''COLLECTING'').';

-- Group lookup: "all rounds for this group" (used in detail endpoint).
CREATE INDEX susu_rounds_group_idx
    ON susu.susu_rounds (susu_group_id, round_number ASC);

COMMENT ON INDEX susu.susu_rounds_group_idx IS
    'Supports ordered round listing for the group detail endpoint '
    'and the disbursement worker when advancing to the next round.';
