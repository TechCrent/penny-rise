-- In production, audit_app is granted INSERT/SELECT/UPDATE/DELETE on
-- audit_log_entries by ops provisioning; V2 then REVOKEs UPDATE/DELETE to
-- enforce append-only. Locally, Flyway is the only setup path, so V2's REVOKE
-- is a no-op (audit_app had nothing) and AuditAppendOnlyGrantsCheck rightly
-- fails. This migration closes that gap.
GRANT INSERT, SELECT ON audit.audit_log_entries TO audit_app;
