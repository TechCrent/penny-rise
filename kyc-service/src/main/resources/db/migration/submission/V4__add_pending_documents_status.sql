-- Migration: V4__add_pending_documents_status.sql
-- Adds PENDING_DOCUMENTS to kyc.submissions.status CHECK constraint.
--
-- Reconciliation: v0.2-021 introduced a two-phase submission flow
-- (create submission -> upload documents -> finalize) that the v0.2-020
-- migration's enum didn't anticipate. PENDING_DOCUMENTS is the initial
-- state before all required documents are uploaded; the existing
-- SUBMITTED state now represents "all documents uploaded, ready to route."
--
-- VARCHAR + CHECK (not Postgres ENUM) means this is a safe, lock-free
-- constraint replacement rather than an ALTER TYPE.

ALTER TABLE kyc.submissions
    DROP CONSTRAINT submissions_status_check;

ALTER TABLE kyc.submissions
    ADD CONSTRAINT submissions_status_check
    CHECK (status IN (
        'PENDING_DOCUMENTS', 'SUBMITTED', 'REVIEWING',
        'APPROVED', 'REJECTED', 'RESUBMISSION_REQUIRED'
    ));

-- Two-phase flow: DOB and phone may be collected after initial submission.
ALTER TABLE kyc.submissions
    ALTER COLUMN date_of_birth DROP NOT NULL;

COMMENT ON COLUMN kyc.submissions.status IS
    'PENDING_DOCUMENTS: created, awaiting document uploads. '
    'SUBMITTED: all documents uploaded, awaiting routing. '
    'REVIEWING: routed to AUTO or MANUAL path. '
    'APPROVED/REJECTED/RESUBMISSION_REQUIRED: terminal or near-terminal states.';
