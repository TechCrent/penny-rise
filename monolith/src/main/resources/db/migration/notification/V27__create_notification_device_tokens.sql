-- ============================================================
-- v0.5-012 : notification.device_tokens (Global V27)
--
-- Built against Schema doc §2.5. registered_at/last_used_at are additions
-- beyond the documented columns — last_used_at is the natural signal for
-- stale-token cleanup ("cleaned up by the notification worker when Expo
-- returns DeviceNotRegistered" per this issue's Description).
-- ============================================================

CREATE TABLE notification.device_tokens (
    id                UUID          NOT NULL,
    user_id           UUID          NOT NULL,
    device_id         VARCHAR(255)  NOT NULL,
    expo_push_token   VARCHAR(255)  NOT NULL,
    platform          VARCHAR(20)   NOT NULL,
    is_active         BOOLEAN       NOT NULL DEFAULT true,
    registered_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_used_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT device_tokens_pk PRIMARY KEY (id),

    CONSTRAINT device_tokens_user_fk
        FOREIGN KEY (user_id)
        REFERENCES user_module.users(id)
        ON DELETE CASCADE,

    CONSTRAINT device_tokens_platform_check
        CHECK (platform IN ('IOS', 'ANDROID')),

    -- This satisfies this issue's AC bullet ("UNIQUE constraint on token")
    -- and is genuinely correct — an Expo push token is issued per app-install
    -- and should never appear on two rows. It is ADDITIVE to the partial index
    -- below, which is what v0.5-014's upsert-by-(user_id, device_id) keys off.
    CONSTRAINT device_tokens_expo_push_token_uk
        UNIQUE (expo_push_token)
);

COMMENT ON TABLE notification.device_tokens IS
    'Expo push tokens per registered device. Per Schema doc §2.5. A user '
    'may have multiple active rows (multiple devices); a device gets a new '
    'row on reinstall/token-refresh, with the prior row is_active flipped '
    'to false — see v0.5-014.';

COMMENT ON COLUMN notification.device_tokens.id IS
    'UUID v7, generated application-side — same convention as notifications.id.';

COMMENT ON COLUMN notification.device_tokens.last_used_at IS
    'Updated by the notification worker on each successful dispatch. '
    'The natural staleness signal for DeviceNotRegistered cleanup.';

-- One active token per device per user. This is the constraint v0.5-014's
-- upsert-and-flip logic depends on (not the expo_push_token UNIQUE above).
CREATE UNIQUE INDEX device_tokens_user_device_active_uk
    ON notification.device_tokens (user_id, device_id)
    WHERE is_active = true;

COMMENT ON INDEX notification.device_tokens_user_device_active_uk IS
    'Partial unique on (user_id, device_id) WHERE is_active = true — one '
    'active token per device per user. v0.5-014 upsert logic keys off this.';

-- Dispatch index: find all active tokens for a user when sending a push.
CREATE INDEX device_tokens_user_active_idx
    ON notification.device_tokens (user_id, is_active)
    WHERE is_active = true;

COMMENT ON INDEX notification.device_tokens_user_active_idx IS
    'Supports push dispatch: "every is_active=true row for the user."';
