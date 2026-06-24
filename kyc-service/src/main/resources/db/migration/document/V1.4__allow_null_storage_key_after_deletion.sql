-- After successful deletion the worker clears storage_key for compliance.
-- Schema doc §8.2: the audit row remains; only the storage path is removed.

ALTER TABLE kyc.submission_documents
    ALTER COLUMN storage_key DROP NOT NULL;

COMMENT ON COLUMN kyc.submission_documents.storage_key IS
    'Object storage path. NULL after successful deletion — bytes are gone and '
    'the path must not be retained (Schema doc §8.2 compliance).';
