-- ============================================================
-- v0.5-018 : idempotency log for the challenge progress consumer (Global V38)
--
-- Same pattern as notification.processed_worker_events (V28/v0.5-013).
-- Written in the same transaction as any progress increment/completion it
-- produced — a duplicate delivery with the same event_id hits the UNIQUE
-- constraint and is discarded before any side effects occur.
-- ============================================================

CREATE TABLE challenge.processed_deposit_events (
    id           UUID          NOT NULL,
    event_id     VARCHAR(255)  NOT NULL,
    processed_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT processed_deposit_events_pk PRIMARY KEY (id),
    CONSTRAINT processed_deposit_events_event_id_uk UNIQUE (event_id)
);

COMMENT ON TABLE challenge.processed_deposit_events IS
    'Idempotency log for the challenge progress consumer (v0.5-018). '
    'Written in the same transaction as any progress increment/completion '
    'it produced.';
