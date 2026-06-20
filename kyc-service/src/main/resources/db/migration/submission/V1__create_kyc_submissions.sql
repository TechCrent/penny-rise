-- Migration: V1__create_kyc_submissions.sql
-- Creates the kyc schema and kyc.submissions table per Schema doc §8.1

CREATE SCHEMA IF NOT EXISTS kyc;

CREATE TABLE kyc.submissions (

    id                      UUID            NOT NULL,

    -- Logical reference only — no FK across databases
    user_id                 UUID            NOT NULL,

    ghana_card_number       VARCHAR(20)     NOT NULL,
    full_name_on_card       VARCHAR(255)    NOT NULL,
    date_of_birth           DATE            NOT NULL,
    phone_number            VARCHAR(20)     NULL,

    status                  VARCHAR(50)     NOT NULL DEFAULT 'SUBMITTED',
    review_path             VARCHAR(50)     NULL,

    provider_decision       VARCHAR(50)     NULL,
    provider_reference      VARCHAR(255)    NULL,

    -- Logical reference to admin.admin_accounts.id (v0.5) — no FK
    reviewer_admin_id       UUID            NULL,

    decision                VARCHAR(50)     NULL,
    decision_reason         TEXT            NULL,

    submitted_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    decided_at              TIMESTAMPTZ     NULL,

    correlation_id          VARCHAR(255)    NULL,

    CONSTRAINT submissions_pkey
        PRIMARY KEY (id),

    CONSTRAINT submissions_status_check
        CHECK (status IN (
            'SUBMITTED', 'REVIEWING', 'APPROVED', 'REJECTED', 'RESUBMISSION_REQUIRED'
        )),

    CONSTRAINT submissions_review_path_check
        CHECK (review_path IS NULL OR review_path IN ('AUTO', 'MANUAL')),

    CONSTRAINT submissions_decision_check
        CHECK (decision IS NULL OR decision IN ('APPROVED', 'REJECTED'))
);

CREATE INDEX submissions_user_id_idx
    ON kyc.submissions (user_id);

CREATE INDEX submissions_status_submitted_at_idx
    ON kyc.submissions (status, submitted_at)
    WHERE status IN ('SUBMITTED', 'REVIEWING');

COMMENT ON TABLE kyc.submissions IS
    'One row per KYC submission attempt. user_id is a LOGICAL reference to '
    'user_module.users.id — no FK across databases. Schema doc §8.1.';

COMMENT ON COLUMN kyc.submissions.user_id IS
    'Logical reference to user_module.users.id in monolith-db. '
    'No foreign key — different database, different service boundary.';

COMMENT ON COLUMN kyc.submissions.review_path IS
    'AUTO: decided algorithmically by the KYC provider. '
    'MANUAL: escalated to a human reviewer. Set when the submission is routed.';