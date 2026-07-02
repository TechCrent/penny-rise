-- ============================================================
-- v0.5-014 : device_id nullable on notification.device_tokens (Global V29)
--
-- POST /api/v1/devices/register accepts only token + platform — no
-- device_id — so this endpoint's inserts can't populate that column.
-- device_id remains available for any future registration path that
-- does supply it. Upsert/dedup for rows created via this endpoint is
-- keyed on expo_push_token (the UNIQUE constraint from v0.5-012/V27).
--
-- The partial unique index device_tokens_user_device_active_uk on
-- (user_id, device_id) WHERE is_active = true still works correctly
-- with NULLs — Postgres treats each NULL as distinct under a unique
-- index, so multiple NULL-device_id rows for the same user do not
-- collide on that index.
-- ============================================================

ALTER TABLE notification.device_tokens ALTER COLUMN device_id DROP NOT NULL;

COMMENT ON COLUMN notification.device_tokens.device_id IS
    'Nullable as of v0.5-014. POST /api/v1/devices/register does not '
    'collect a device_id from the client — upsert and dedup for rows '
    'it creates are keyed on expo_push_token (the UNIQUE constraint '
    'from v0.5-012/V27), not (user_id, device_id).';
