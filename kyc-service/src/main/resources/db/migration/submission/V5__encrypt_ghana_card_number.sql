-- Migration: V5__encrypt_ghana_card_number.sql
-- Replaces plaintext ghana_card_number with an encrypted column.
--
-- Application-layer AES-256-GCM encryption (via a JPA AttributeConverter)
-- is used rather than pgcrypto, so the encryption key is managed through
-- the application's secret configuration (consistent with the JWT signing
-- key and other application secrets) rather than living in the database
-- itself. A compromised DB credential alone is insufficient to decrypt
-- the column — the application's encryption key is also required.
--
-- Column type changes from VARCHAR(20) to TEXT because AES-GCM ciphertext
-- (base64-encoded, includes a 12-byte nonce and a 16-byte auth tag) is
-- longer than the original 20-character plaintext.
--
-- No data migration needed — this table has no production rows yet (v0.2
-- has not shipped). If this were a populated table, a backfill migration
-- re-encrypting existing values would be required here instead.

ALTER TABLE kyc.submissions
    ALTER COLUMN ghana_card_number TYPE TEXT;

COMMENT ON COLUMN kyc.submissions.ghana_card_number IS
    'AES-256-GCM encrypted at the application layer (see GhanaCardEncryptionConverter). '
    'Never stored or logged in plaintext. Base64-encoded ciphertext including nonce + auth tag.';
