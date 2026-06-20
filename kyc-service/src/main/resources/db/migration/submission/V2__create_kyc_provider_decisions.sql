-- Migration: V2__create_kyc_provider_decisions.sql
-- Creates kyc.provider_decisions.
--
-- NOTE: NOT in canonical Schema doc §8 as a separate table.
-- Added per issue request for append-only log of automated provider responses.
-- Flag for Schema doc sign-off before relying on this beyond v0.2.

CREATE TABLE kyc.provider_decisions (

    id                  UUID            NOT NULL,
    submission_id       UUID            NOT NULL,

    provider_name       VARCHAR(100)    NOT NULL,
    provider_reference  VARCHAR(255)    NOT NULL,
    decision            VARCHAR(50)     NOT NULL,
    confidence_score    NUMERIC(5,4)    NULL,
    raw_response        JSONB           NULL,

    requested_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    responded_at        TIMESTAMPTZ     NULL,

    CONSTRAINT provider_decisions_pkey
        PRIMARY KEY (id),

    CONSTRAINT provider_decisions_submission_fk
        FOREIGN KEY (submission_id)
        REFERENCES kyc.submissions (id)
        ON DELETE CASCADE,

    CONSTRAINT provider_decisions_decision_check
        CHECK (decision IN ('PASS', 'FLAGGED', 'FAIL', 'ERROR', 'TIMEOUT'))
);

CREATE INDEX provider_decisions_submission_id_idx
    ON kyc.provider_decisions (submission_id, requested_at);

COMMENT ON TABLE kyc.provider_decisions IS
    'Append-only log of every automated KYC-provider response per submission. '
    'NOT in the canonical Schema doc §8 — added in v0.2-020, pending doc sign-off. '
    'The current/authoritative decision is also mirrored onto '
    'kyc.submissions.provider_decision for fast access without a join.';