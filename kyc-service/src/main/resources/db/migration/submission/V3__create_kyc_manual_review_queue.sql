-- Migration: V3__create_kyc_manual_review_queue.sql
-- Creates kyc.manual_review_queue.
--
-- NOTE: NOT in canonical Schema doc §8. Implements claim/lock concurrency
-- control from Wireframes doc A5. Flag for Schema doc sign-off.

CREATE TABLE kyc.manual_review_queue (

    id                      UUID            NOT NULL,
    submission_id           UUID            NOT NULL,

    -- Logical reference to admin.admin_accounts.id (v0.5) — no FK
    claimed_by_admin_id     UUID            NULL,
    claimed_at              TIMESTAMPTZ     NULL,
    claim_expires_at        TIMESTAMPTZ     NULL,

    flag_reason             VARCHAR(255)    NULL,

    entered_queue_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    removed_from_queue_at   TIMESTAMPTZ     NULL,

    CONSTRAINT manual_review_queue_pkey
        PRIMARY KEY (id),

    CONSTRAINT manual_review_queue_submission_fk
        FOREIGN KEY (submission_id)
        REFERENCES kyc.submissions (id)
        ON DELETE CASCADE,

    CONSTRAINT manual_review_queue_unique_active_submission
        UNIQUE (submission_id)
);

CREATE INDEX manual_review_queue_active_idx
    ON kyc.manual_review_queue (entered_queue_at)
    WHERE removed_from_queue_at IS NULL;

CREATE INDEX manual_review_queue_claimed_by_idx
    ON kyc.manual_review_queue (claimed_by_admin_id)
    WHERE claimed_by_admin_id IS NOT NULL;

COMMENT ON TABLE kyc.manual_review_queue IS
    'Submissions routed to MANUAL review, with claim/lock semantics per the '
    'Wireframes doc A5. NOT in the canonical Schema doc §8 — added in '
    'v0.2-020, pending doc sign-off.';

COMMENT ON COLUMN kyc.manual_review_queue.claim_expires_at IS
    'claimed_at + 30 minutes. After this passes, the claim is considered '
    'stale and another reviewer may claim the submission.';