-- ============================================================
-- v0.4-021 : susu.susu_contribution_reminders
--
-- Tracks which reminder events have been emitted for each
-- contribution. One row per (contribution_id, reminder_type).
--
-- UNIQUE (contribution_id, reminder_type) is the idempotency
-- guard — the reminder job INSERT-OR-IGNOREs into this table.
-- If the row already exists, the reminder was already sent;
-- the job skips the event emission for that combination.
--
-- reminder_type values: '48H' and '24H'.
-- ============================================================

CREATE TABLE susu.susu_contribution_reminders (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    contribution_id  UUID         NOT NULL,
    reminder_type    VARCHAR(10)  NOT NULL,
    emitted_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT susu_contribution_reminders_pk
        PRIMARY KEY (id),

    CONSTRAINT susu_contribution_reminders_contrib_fk
        FOREIGN KEY (contribution_id)
        REFERENCES susu.susu_contributions(id)
        ON DELETE CASCADE,

    CONSTRAINT susu_contribution_reminders_type_check
        CHECK (reminder_type IN ('48H', '24H')),

    -- The core idempotency constraint: only one reminder per type per contribution
    CONSTRAINT susu_contribution_reminders_unique
        UNIQUE (contribution_id, reminder_type)
);

COMMENT ON TABLE susu.susu_contribution_reminders IS
    'Tracks emitted contribution reminder events to prevent duplicates. '
    'One row per (contribution_id, reminder_type). Inserted by the reminder job '
    'before publishing the event. ON CONFLICT DO NOTHING provides idempotency. '
    'FK cascades so rows are cleaned up when a contribution is deleted.';

COMMENT ON COLUMN susu.susu_contribution_reminders.reminder_type IS
    '48H = reminder emitted 48 hours before due date. '
    '24H = reminder emitted 24 hours before due date.';

CREATE INDEX susu_contribution_reminders_contrib_idx
    ON susu.susu_contribution_reminders (contribution_id);

COMMENT ON INDEX susu.susu_contribution_reminders_contrib_idx IS
    'Supports "has this contribution had a 48H or 24H reminder sent?" lookups '
    'by the reminder job for a batch of contribution IDs.';
