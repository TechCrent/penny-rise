-- Migration: V2__add_moolre_webhook_provider.sql
-- Widens webhook.processed_webhook_events.provider to include MOOLRE
-- alongside PAYSTACK.

ALTER TABLE webhook.processed_webhook_events
    DROP CONSTRAINT processed_webhook_events_provider_check;

ALTER TABLE webhook.processed_webhook_events
    ADD CONSTRAINT processed_webhook_events_provider_check
        CHECK (provider IN ('PAYSTACK', 'MOOLRE'));
