-- ============================================================
-- v0.5-013 : idempotency log for the notification worker (Global V28)
--
-- Same pattern as webhook.processed_webhook_events (Payments, §7.3) and
-- kyc.processed_document_events (§8.5): a UNIQUE constraint on event_id is
-- the entire dedup mechanism. Needed because a single domain event can
-- produce up to three notification rows (in-app/push/email), so no single
-- notifications row is itself a reliable "have I processed this event"
-- marker.
-- ============================================================

CREATE TABLE notification.processed_worker_events (
    id           UUID          NOT NULL,
    event_id     VARCHAR(255)  NOT NULL,
    event_type   VARCHAR(100)  NOT NULL,
    processed_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT processed_worker_events_pk PRIMARY KEY (id),
    CONSTRAINT processed_worker_events_event_id_uk UNIQUE (event_id)
);

COMMENT ON TABLE notification.processed_worker_events IS
    'Idempotency log for the notification worker (v0.5-013). Written in the '
    'same transaction as the notification row(s) it produced — if the '
    'transaction commits, both the notifications and the dedup marker exist '
    'together; if it rolls back, neither does.';

CREATE INDEX processed_worker_events_processed_at_idx
    ON notification.processed_worker_events (processed_at);

COMMENT ON INDEX notification.processed_worker_events_processed_at_idx IS
    'For a future retention/cleanup job — this table grows forever otherwise, '
    'same caveat as kyc.processed_document_events.';
