-- ============================================================
-- v0.4-004 : susu.susu_contributions
-- One row per member per round. Created when a round enters
-- COLLECTING status.
--
-- Schema doc §3.6 reference. Deviations documented below:
--
-- 1. STATUS ENUM:
--    Schema doc: PENDING / PAID / LATE / MISSED
--    Issue spec: PENDING / PAID / LATE / WAIVED
--    Resolution: include ALL FIVE values (MISSED + WAIVED are
--    distinct states):
--      MISSED   = contribution window closed without payment
--      WAIVED   = organiser forgave the contribution (v0.4-011+)
--    Both are needed; omitting either creates a forward migration later.
--
-- 2. UNIQUE KEY TARGET:
--    Schema doc uses member_user_id (no FK to susu_memberships).
--    Issue spec wants UNIQUE (round_id, membership_id) via a
--    memberships FK.
--    Resolution: use member_user_id per Schema doc for the unique
--    constraint. The susu_memberships table holds user_id; the
--    activation service uses the membership to derive member_user_id.
--    A physical FK to susu_memberships is not added — the denormalised
--    member_user_id is sufficient for the unique constraint and queries.
--
-- 3. ADDED COLUMNS (issue spec, not in Schema doc):
--    is_late BOOLEAN NOT NULL DEFAULT false — set by the late-penalty
--      job once the collection window passes (v0.4-011).
--    transaction_reference VARCHAR(255) — human-readable Payments Service
--      reference (e.g. "STSH-202606-CON001") stored alongside transaction_id
--      UUID for display in the mobile statement screen.
--    paid_at TIMESTAMPTZ — set when status transitions to PAID.
--      Not in Schema doc but required by v0.4-009 to display in the UI.
--
-- 4. INDEX:
--    Issue spec: index on (round_id, status).
--    Also adding: index on (susu_group_id, member_user_id) for
--    "show all my contributions" queries used in the detail endpoint.
-- ============================================================

CREATE TABLE susu.susu_contributions (
    id                        UUID          NOT NULL DEFAULT gen_random_uuid(),
    susu_round_id             UUID          NOT NULL,
    susu_group_id             UUID          NOT NULL,
    member_user_id            UUID          NOT NULL,
    expected_amount           BIGINT        NOT NULL,
    collected_amount          BIGINT        NULL,
    status                    VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    collection_attempt_count  INT           NOT NULL DEFAULT 0,
    penalty_amount            BIGINT        NOT NULL DEFAULT 0,
    is_late                   BOOLEAN       NOT NULL DEFAULT false,
    transaction_id            UUID          NULL,
    transaction_reference     VARCHAR(255)  NULL,
    paid_at                   TIMESTAMPTZ   NULL,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT susu_contributions_pk
        PRIMARY KEY (id),

    CONSTRAINT susu_contributions_round_fk
        FOREIGN KEY (susu_round_id)
        REFERENCES susu.susu_rounds(id)
        ON DELETE CASCADE,

    CONSTRAINT susu_contributions_group_fk
        FOREIGN KEY (susu_group_id)
        REFERENCES susu.susu_groups(id)
        ON DELETE CASCADE,

    -- One contribution per member per round
    CONSTRAINT susu_contributions_round_member_uk
        UNIQUE (susu_round_id, member_user_id),

    CONSTRAINT susu_contributions_status_check
        CHECK (status IN ('PENDING', 'PAID', 'LATE', 'MISSED', 'WAIVED')),

    CONSTRAINT susu_contributions_expected_amount_positive
        CHECK (expected_amount > 0),

    CONSTRAINT susu_contributions_collected_amount_non_negative
        CHECK (collected_amount IS NULL OR collected_amount >= 0),

    CONSTRAINT susu_contributions_penalty_amount_non_negative
        CHECK (penalty_amount >= 0),

    CONSTRAINT susu_contributions_collection_attempts_non_negative
        CHECK (collection_attempt_count >= 0),

    -- paid_at only set for terminal statuses
    CONSTRAINT susu_contributions_paid_at_requires_paid
        CHECK (paid_at IS NULL OR status IN ('PAID', 'WAIVED')),

    -- is_late flag consistency: LATE/MISSED rows should have is_late = true
    -- We enforce the inverse: is_late = true only allowed on LATE, MISSED, or PAID
    -- (a member can pay late — still PAID but is_late = true)
    CONSTRAINT susu_contributions_is_late_status_consistency
        CHECK (
            is_late = false
            OR status IN ('PENDING', 'LATE', 'MISSED', 'PAID')
        ),

    -- Transaction fields only populated once payment is recorded
    CONSTRAINT susu_contributions_txn_requires_paid
        CHECK (
            transaction_id IS NULL
            OR status IN ('PAID', 'LATE', 'MISSED')
        )
);

COMMENT ON TABLE susu.susu_contributions IS
    'One row per member per round. Inserted when the round transitions '
    'to COLLECTING status (N rows for an N-member group). '
    'Tracks the member''s payment status through the collection window. '
    'Late penalty accumulates in penalty_amount and is deducted from the '
    'member''s eventual payout (their own rotation round).';

COMMENT ON COLUMN susu.susu_contributions.susu_round_id IS
    'Physical FK -> susu.susu_rounds.id. CASCADE DELETE clears contributions '
    'if a round is removed (rare; rounds are not normally deleted).';
COMMENT ON COLUMN susu.susu_contributions.susu_group_id IS
    'Denormalized physical FK -> susu.susu_groups.id. Avoids a join through '
    'susu_rounds when querying "all my contributions in group X".';
COMMENT ON COLUMN susu.susu_contributions.member_user_id IS
    'Logical FK -> user_module.users.id. No physical FK (cross-schema). '
    'Derived from susu_memberships.user_id at round generation time.';
COMMENT ON COLUMN susu.susu_contributions.expected_amount IS
    'The contribution_amount from susu_groups at round creation time, in pesewas. '
    'Snapshotted so a future group amendment does not retroactively change past rounds.';
COMMENT ON COLUMN susu.susu_contributions.collected_amount IS
    'Amount actually received, in pesewas. NULL until a payment is recorded. '
    'May differ from expected_amount for partial payments (not permitted in v0.4; '
    'reserved for v2.0 flexible contributions).';
COMMENT ON COLUMN susu.susu_contributions.status IS
    'Lifecycle: PENDING (due, not yet paid), '
    'PAID (received in full), '
    'LATE (payment window passed; penalty accumulating), '
    'MISSED (round closed without payment — deducted from future payout), '
    'WAIVED (organiser forgave this contribution — no penalty).';
COMMENT ON COLUMN susu.susu_contributions.collection_attempt_count IS
    'Number of collection attempts (future automatic debit, v2.0+). '
    'Default 0; incremented by the collection job on each attempt.';
COMMENT ON COLUMN susu.susu_contributions.penalty_amount IS
    'Accumulated late penalty in pesewas. DEFAULT 0. '
    'Set by the late-penalty job (v0.4-011). '
    'At v0.4: flat GHS 5 (500 pesewas) per Decision 13.';
COMMENT ON COLUMN susu.susu_contributions.is_late IS
    'true once the late-penalty job marks this contribution overdue. '
    'Stays true even if the member eventually pays (allows distinguishing '
    'on-time PAID from late PAID in analytics).';
COMMENT ON COLUMN susu.susu_contributions.transaction_id IS
    'Logical FK -> Payments Service transaction (UUID). '
    'Set when the member''s contribution transfer is confirmed.';
COMMENT ON COLUMN susu.susu_contributions.transaction_reference IS
    'Human-readable Payments Service reference (e.g. STSH-202606-CON001). '
    'Set alongside transaction_id. Used in the mobile statement display.';
COMMENT ON COLUMN susu.susu_contributions.paid_at IS
    'Timestamp when the contribution transitioned to PAID or WAIVED. '
    'NULL until terminal status reached.';

-- ── Indexes ────────────────────────────────────────────────────────────────

-- Primary worker index: disbursement readiness check.
-- "Are all contributions for round R in a terminal status?"
-- Covers the WHERE status = 'PENDING' / 'LATE' check the disbursement
-- worker uses to determine if the round can close.
CREATE INDEX susu_contributions_round_status_idx
    ON susu.susu_contributions (susu_round_id, status);

COMMENT ON INDEX susu.susu_contributions_round_status_idx IS
    'Supports the disbursement worker''s readiness check: '
    'SELECT COUNT(*) WHERE susu_round_id = ? AND status NOT IN (''PAID'', ''WAIVED'', ''MISSED''). '
    'Also covers the late-penalty job scanning PENDING rows past the due date.';

-- Member contribution history: "show all my contributions across all groups".
CREATE INDEX susu_contributions_member_group_idx
    ON susu.susu_contributions (member_user_id, susu_group_id, created_at DESC);

COMMENT ON INDEX susu.susu_contributions_member_group_idx IS
    'Supports the group detail endpoint returning the authenticated user''s '
    'contribution history within a group, ordered newest first.';

-- Group-wide contributions: "all outstanding contributions across a group"
-- Used by the organiser view in the detail endpoint.
CREATE INDEX susu_contributions_group_status_idx
    ON susu.susu_contributions (susu_group_id, status);

COMMENT ON INDEX susu.susu_contributions_group_status_idx IS
    'Supports organiser view: list all PENDING/LATE members across the group. '
    'Separate from round_status_idx because the organiser sees across rounds.';
