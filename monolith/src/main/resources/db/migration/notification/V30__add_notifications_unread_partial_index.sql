-- ============================================================
-- v0.5-015 : partial index dedicated to the unread_count query (Global V30)
--
-- v0.5-012's notifications_user_read_idx (user_id, read_at) already
-- supports this query reasonably well, but it indexes every row (read and
-- unread). This partial index only indexes unread rows — smaller, and a
-- tighter match for exactly "COUNT(*) WHERE user_id = ? AND read_at IS
-- NULL", which is the hot path this issue's sub-20ms performance DoD targets.
-- ============================================================

CREATE INDEX notifications_user_unread_idx
    ON notification.notifications (user_id)
    WHERE read_at IS NULL;

COMMENT ON INDEX notification.notifications_user_unread_idx IS
    'Partial index for unread_count (GET /api/v1/notifications) — only '
    'unread rows are indexed, since read notifications never need to be '
    'found by this query. Complements, does not replace, '
    'notifications_user_read_idx from v0.5-012/V26.';
