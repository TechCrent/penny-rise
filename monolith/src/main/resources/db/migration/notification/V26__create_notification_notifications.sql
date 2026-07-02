-- ============================================================
-- v0.5-012 : notification.notifications (Global V26)
--
-- Built against Schema doc §2.4, NOT the §8.1 citation in this issue's AC
-- (§8 is the KYC Service chapter — unrelated). delivery_status,
-- delivery_attempts, and deep_link are included despite not appearing in
-- this issue's literal column list because v0.5-013's AC explicitly requires
-- them (worker writes delivery_status; caps delivery_attempts at 3).
-- ============================================================

CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notifications (
    id                  UUID          NOT NULL,
    user_id             UUID          NOT NULL,
    notification_type   VARCHAR(50)   NOT NULL,
    channel             VARCHAR(50)   NOT NULL,
    title               VARCHAR(255)  NOT NULL,
    body                TEXT          NOT NULL,
    deep_link           VARCHAR(500)  NULL,
    payload             JSONB         NULL,
    delivery_status     VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    delivery_attempts   INT           NOT NULL DEFAULT 0,
    read_at             TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT notifications_pk PRIMARY KEY (id),

    CONSTRAINT notifications_user_fk
        FOREIGN KEY (user_id)
        REFERENCES user_module.users(id)
        ON DELETE CASCADE,

    CONSTRAINT notifications_channel_check
        CHECK (channel IN ('PUSH', 'EMAIL', 'IN_APP')),

    CONSTRAINT notifications_delivery_status_check
        CHECK (delivery_status IN ('PENDING', 'DELIVERED', 'FAILED', 'SUPPRESSED')),

    CONSTRAINT notifications_delivery_attempts_check
        CHECK (delivery_attempts BETWEEN 0 AND 3)
);

COMMENT ON TABLE notification.notifications IS
    'Every outbound notification, regardless of channel. Doubles as the '
    'in-app inbox (channel=IN_APP rows are the notification history a user '
    'sees in-app). Per Schema doc §2.4.';

COMMENT ON COLUMN notification.notifications.id IS
    'UUID v7, generated application-side — no DB-side DEFAULT because '
    'gen_random_uuid() produces v4, not v7.';

COMMENT ON COLUMN notification.notifications.delivery_status IS
    'PENDING at insert; flipped by the notification worker (v0.5-013). '
    'SUPPRESSED covers "user blocked email" suppression-list case.';

COMMENT ON COLUMN notification.notifications.delivery_attempts IS
    'Capped at 3 by the CHECK constraint, matching v0.5-013 AC '
    '("retries failed sends up to 3 times").';

CREATE INDEX notifications_user_read_idx
    ON notification.notifications (user_id, read_at);

COMMENT ON INDEX notification.notifications_user_read_idx IS
    'Supports unread-count queries: WHERE user_id = ? AND read_at IS NULL.';

CREATE INDEX notifications_user_created_idx
    ON notification.notifications (user_id, created_at DESC);

COMMENT ON INDEX notification.notifications_user_created_idx IS
    'Supports inbox pagination (v0.5-015): newest-first list for a user.';
