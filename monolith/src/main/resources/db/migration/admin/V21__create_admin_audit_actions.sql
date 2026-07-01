-- ============================================================
-- v0.5-002 (part 1) : admin.admin_audit_actions
--
-- Schema doc §2.2 reference. Column names follow Schema doc, NOT
-- the issue description's target_entity_type/target_entity_id/
-- performed_at — those are stale terms; Schema doc uses target_type,
-- target_id, created_at, matching the naming already established in
-- audit.audit_log_entries (§9.1) for consistency across the two
-- audit-adjacent tables.
--
-- APPEND-ONLY ENFORCEMENT: Schema doc §2.2 describes this table as
-- "write-once at the application level," with the Audit Log Service
-- (separate database, §9.1) as the authoritative append-only store.
-- This issue's Definition of Done explicitly asks for a DB-level
-- REVOKE-based negative test here too. Building that as defense in
-- depth — it does not contradict the Schema doc, it strengthens
-- beyond its stated minimum. Follows the exact pattern already used
-- for ledger.ledger_entries in the Payments Service (Folder Structure
-- doc §3.6, V4__revoke_modify_on_ledger_entries.sql).
-- ============================================================

CREATE TABLE admin.admin_audit_actions (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    admin_account_id  UUID          NOT NULL,
    action_type       VARCHAR(100)  NOT NULL,
    target_type       VARCHAR(50)   NOT NULL,
    target_id         UUID          NOT NULL,
    payload           JSONB         NULL,
    ip_address        VARCHAR(45)   NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT admin_audit_actions_pk
        PRIMARY KEY (id),

    CONSTRAINT admin_audit_actions_admin_fk
        FOREIGN KEY (admin_account_id)
        REFERENCES admin.admin_accounts(id)
        ON DELETE RESTRICT,

    CONSTRAINT admin_audit_actions_action_type_not_blank
        CHECK (length(trim(action_type)) > 0),

    CONSTRAINT admin_audit_actions_target_type_not_blank
        CHECK (length(trim(target_type)) > 0)
);

COMMENT ON TABLE admin.admin_audit_actions IS
    'Local, fast-access copy of every action an admin takes against the '
    'platform — suspending a user, resolving a dispute, approving KYC, etc. '
    'Write-once at the application level; this table''s DB-level grants '
    'additionally REVOKE UPDATE/DELETE from the application role (see '
    'V21_1) as defense in depth. The Audit Log Service (separate database, '
    'audit.audit_log_entries) is the platform''s authoritative, fully '
    'isolated append-only store — this table exists so the admin '
    'dashboard''s recent-activity view stays fast even if the Audit Log '
    'Service is unreachable.';

COMMENT ON COLUMN admin.admin_audit_actions.admin_account_id IS
    'Physical FK -> admin.admin_accounts.id. ON DELETE RESTRICT — an '
    'admin account with audit history can never be hard-deleted, only '
    'deactivated (is_active=false), preserving the integrity of the '
    'audit trail.';
COMMENT ON COLUMN admin.admin_audit_actions.action_type IS
    'e.g. KYC_APPROVED, USER_SUSPENDED, REFUND_ISSUED, DISPUTE_RESOLVED. '
    'Free-text but conventionally SCREAMING_SNAKE_CASE; no CHECK enum '
    'since the action vocabulary grows continuously as new admin '
    'capabilities ship.';
COMMENT ON COLUMN admin.admin_audit_actions.target_type IS
    'What kind of entity was acted on: USER, DISPUTE, SUSU_GROUP, VAULT, '
    'ADMIN_ACCOUNT, etc. Paired with target_id to form a logical, '
    'non-FK polymorphic reference — the target may live in a different '
    'module (and conceptually in a different service) than admin itself.';
COMMENT ON COLUMN admin.admin_audit_actions.payload IS
    'JSONB containing { "before": {...}, "after": {...}, "reason": "..." } '
    'per Schema doc §2.2. Shape varies by action_type; no fixed schema '
    'is enforced — this is schema-on-read by design, same rationale as '
    'audit.audit_log_entries.payload (§9.1).';
COMMENT ON COLUMN admin.admin_audit_actions.ip_address IS
    'Admin''s source IP at the time of the action. VARCHAR(45) sized for '
    'IPv6 (max 45 chars including zone ID). NULL is permitted for actions '
    'triggered by system processes acting "as" an admin context (rare; '
    'flagged for review if it occurs in practice).';

-- ── Indexes ────────────────────────────────────────────────────────────────

CREATE INDEX admin_audit_actions_admin_created_idx
    ON admin.admin_audit_actions (admin_account_id, created_at DESC);

COMMENT ON INDEX admin.admin_audit_actions_admin_created_idx IS
    'Supports "recent actions by this admin" — used on the admin detail '
    'screen (referenced but not wireframed in A10) showing an individual '
    'staff member''s activity history.';

CREATE INDEX admin_audit_actions_target_created_idx
    ON admin.admin_audit_actions (target_type, target_id, created_at DESC);

COMMENT ON INDEX admin.admin_audit_actions_target_created_idx IS
    'Supports "history of actions on this entity" — e.g. all admin '
    'actions ever taken against a specific user or susu group, ordered '
    'newest first.';

CREATE INDEX admin_audit_actions_action_created_idx
    ON admin.admin_audit_actions (action_type, created_at DESC);

COMMENT ON INDEX admin.admin_audit_actions_action_created_idx IS
    'Supports "all KYC approvals this week" style operational queries — '
    'filtering by action_type across all admins and targets.';
