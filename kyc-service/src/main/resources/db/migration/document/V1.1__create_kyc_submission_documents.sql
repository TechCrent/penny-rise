-- Migration: V1.1__create_kyc_submission_documents.sql
-- Creates kyc.submission_documents per Schema doc §8.2 (kyc_documents).
-- Versioned 1.1 (not 1) to guarantee it runs after submission/V1 which it FK-depends on.

CREATE TABLE kyc.submission_documents (

    id                          UUID            NOT NULL,
    submission_id               UUID            NOT NULL,

    document_type               VARCHAR(50)     NOT NULL,
    storage_provider            VARCHAR(50)     NOT NULL,
    storage_key                 VARCHAR(500)    NOT NULL,
    content_type                VARCHAR(100)    NOT NULL,
    size_bytes                  BIGINT          NOT NULL,
    sha256_hash                 VARCHAR(255)    NOT NULL,

    deletion_status             VARCHAR(50)     NOT NULL DEFAULT 'RETAINED',
    deletion_scheduled_at       TIMESTAMPTZ     NULL,
    deletion_completed_at       TIMESTAMPTZ     NULL,
    deletion_failure_count      INT             NOT NULL DEFAULT 0,

    uploaded_at                 TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT submission_documents_pkey
        PRIMARY KEY (id),

    CONSTRAINT submission_documents_submission_fk
        FOREIGN KEY (submission_id)
        REFERENCES kyc.submissions (id)
        ON DELETE CASCADE,

    CONSTRAINT submission_documents_document_type_check
        CHECK (document_type IN ('FRONT_OF_CARD', 'BACK_OF_CARD', 'SELFIE')),

    CONSTRAINT submission_documents_storage_provider_check
        CHECK (storage_provider IN ('SUPABASE', 'LOCAL')),

    CONSTRAINT submission_documents_deletion_status_check
        CHECK (deletion_status IN (
            'RETAINED', 'PENDING_DELETION', 'DELETED', 'DELETE_FAILED'
        )),

    CONSTRAINT submission_documents_deletion_failure_count_nonneg
        CHECK (deletion_failure_count >= 0),

    CONSTRAINT submission_documents_unique_type_per_submission
        UNIQUE (submission_id, document_type)
);

CREATE INDEX submission_documents_submission_id_type_idx
    ON kyc.submission_documents (submission_id, document_type);

CREATE INDEX submission_documents_deletion_worker_idx
    ON kyc.submission_documents (deletion_status, deletion_scheduled_at)
    WHERE deletion_status IN ('PENDING_DELETION', 'DELETE_FAILED');

COMMENT ON TABLE kyc.submission_documents IS
    'Document metadata for KYC submissions. Image bytes live in Supabase '
    'Storage (or local filesystem in dev); this row holds the reference. '
    'Deferred-deletion state machine per Schema doc §8.2.';

COMMENT ON COLUMN kyc.submission_documents.deletion_scheduled_at IS
    'submission.decided_at + 24 hours. The 24h grace window allows quick '
    'correction if a reviewer realises a mistake shortly after deciding.';

COMMENT ON COLUMN kyc.submission_documents.deletion_failure_count IS
    'Incremented on each failed deletion attempt. Escalated to ops at 5.';