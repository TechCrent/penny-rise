-- Migration: V1.2__create_kyc_document_deletion_jobs.sql
-- Creates kyc.document_deletion_jobs per Schema doc §8.3 (kyc_deletion_attempts).
-- Versioned 1.2 so it runs after V1.1 (submission_documents) which it FK-depends on.

CREATE TABLE kyc.document_deletion_jobs (

    id                  UUID            NOT NULL,
    document_id         UUID            NOT NULL,

    attempted_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    outcome             VARCHAR(50)     NOT NULL,
    error_message       TEXT            NULL,
    error_class         VARCHAR(255)    NULL,
    storage_provider    VARCHAR(50)     NOT NULL,
    correlation_id      VARCHAR(255)    NULL,

    CONSTRAINT document_deletion_jobs_pkey
        PRIMARY KEY (id),

    CONSTRAINT document_deletion_jobs_document_fk
        FOREIGN KEY (document_id)
        REFERENCES kyc.submission_documents (id)
        ON DELETE CASCADE,

    CONSTRAINT document_deletion_jobs_outcome_check
        CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX document_deletion_jobs_document_id_idx
    ON kyc.document_deletion_jobs (document_id, attempted_at);

COMMENT ON TABLE kyc.document_deletion_jobs IS
    'Append-only audit trail of every attempt (successful or failed) to '
    'delete a kyc.submission_documents row''s bytes from storage. '
    'Schema doc §8.3 (kyc_deletion_attempts).';

COMMENT ON COLUMN kyc.document_deletion_jobs.error_class IS
    'Exception class name when outcome=FAILURE — e.g. '
    '"java.net.SocketTimeoutException" — for fast triage without log diving.';