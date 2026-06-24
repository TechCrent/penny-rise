-- Migration: V1.3__create_processed_document_events.sql
-- Creates kyc.processed_document_events for webhook idempotency.
--
-- Mirrors the Payments Service's webhook.processed_webhook_events pattern
-- (System Design doc, Step 4 of the deposit walkthrough): a UNIQUE
-- constraint on the provider's event ID protects against duplicate
-- delivery. If the unique constraint fails on insert, the handler treats
-- it as an already-processed duplicate and returns 200 immediately
-- without reprocessing.
--
-- NOTE: not in the canonical Schema doc §8 as a named table — added here
-- per this issue's idempotency requirement, following the exact pattern
-- already established for Payments webhooks. Flag for Schema doc sign-off
-- alongside the other v0.2-020/021 additions.
--
-- Rollback strategy: forward-only.

CREATE TABLE kyc.processed_document_events (

    id                   UUID            NOT NULL,
    provider_event_id    VARCHAR(255)    NOT NULL,
    document_id          UUID            NULL,
    processing_status    VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    received_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    processed_at         TIMESTAMPTZ     NULL,
    correlation_id       VARCHAR(255)    NULL,

    CONSTRAINT processed_document_events_pkey
        PRIMARY KEY (id),

    CONSTRAINT processed_document_events_provider_event_id_unique
        UNIQUE (provider_event_id),

    CONSTRAINT processed_document_events_processing_status_check
        CHECK (processing_status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

COMMENT ON TABLE kyc.processed_document_events IS
    'Webhook idempotency table for document-upload confirmation events. '
    'Mirrors Payments webhook.processed_webhook_events. A duplicate '
    'provider_event_id hits the UNIQUE constraint and the handler returns '
    '200 without reprocessing.';
