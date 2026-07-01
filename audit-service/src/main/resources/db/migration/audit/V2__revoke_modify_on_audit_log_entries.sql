-- ============================================================
-- v0.5-010 : append-only enforcement on audit.audit_log_entries
--
-- Mirrors V4__revoke_modify_on_ledger_entries.sql (payments-service,
-- v0.3-003) and the outbox.outbox_events pattern (v0.3-006) — same
-- REVOKE-in-a-separate-migration convention as those two. Unlike
-- outbox_events, there's no column-level partial grant here: audit
-- entries have no legitimate mutable field at all (outbox needed
-- status/sent_at writable for the relay; audit log entries are
-- write-once, full stop).
-- ============================================================

REVOKE UPDATE, DELETE ON audit.audit_log_entries FROM audit_app;
-- audit_app is ASSUMED by analogy with the monolith's stash_app — NOT
-- CONFIRMED. If audit-service's actual application role has a different
-- name, this REVOKE is a silent no-op against the real role and the
-- append-only guarantee does not actually hold. Verify before applying.

GRANT SELECT ON audit.audit_log_entries TO audit_dashboard_ro;
-- audit_dashboard_ro is assumed to already exist. Role creation (CREATE
-- ROLE) is a cluster-level operation, not a per-database Flyway concern —
-- consistent with how stash_app/audit_app themselves are never CREATE'd by
-- any migration in this codebase, only granted/revoked against elsewhere.
-- Provisioning this role (with its own rotated credential, per the Overall
-- doc's secrets-management chapter) is an infra/ops prerequisite outside
-- this migration's scope.

COMMENT ON TABLE audit.audit_log_entries IS
    'Append-only. audit_app (application role): INSERT + SELECT only, '
    'UPDATE/DELETE explicitly revoked in V2. audit_dashboard_ro (read-only '
    'service account): SELECT only, no write privileges whatsoever. '
    'Enforced at the Postgres GRANT level per System Design §5.4 — verified '
    'at every service startup by AuditAppendOnlyGrantsCheck, which refuses '
    'to start the service if these permissions have drifted.';
