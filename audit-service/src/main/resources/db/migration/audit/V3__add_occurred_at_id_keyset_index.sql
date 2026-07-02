-- ============================================================
-- v0.5-011 : keyset pagination support for the admin audit log read API
-- ============================================================

CREATE INDEX audit_log_entries_occurred_at_id_idx
    ON audit.audit_log_entries (occurred_at DESC, id DESC);

COMMENT ON INDEX audit.audit_log_entries_occurred_at_id_idx IS
    'Supports keyset (cursor) pagination for GET /api/v1/admin/audit-log: '
    'WHERE (occurred_at, id) < (:cursor_occurred_at, :cursor_id) ORDER BY '
    'occurred_at DESC, id DESC. Needed because none of v0.5-009''s indexes '
    'cover the unfiltered newest-first base case.';
