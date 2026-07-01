-- ============================================================
-- v0.5-007 : prevent duplicate OPEN/IN_REVIEW disputes on the same entity
-- ============================================================

CREATE UNIQUE INDEX disputes_active_per_entity_uk
    ON admin.disputes (raised_by_user_id, related_entity_type, related_entity_id)
    WHERE status IN ('OPEN', 'IN_REVIEW');

COMMENT ON INDEX admin.disputes_active_per_entity_uk IS
    'Enforces "no duplicate active dispute on the same entity" (Issue v0.5-007 AC) '
    'at the database level. The application also pre-checks before insert for a '
    'friendlier error path, but this index is the actual race-proof guarantee — '
    'the pre-check alone has a TOCTOU window between two concurrent requests.';
