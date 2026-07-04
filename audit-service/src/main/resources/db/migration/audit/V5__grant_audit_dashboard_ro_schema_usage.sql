-- V2 granted SELECT ON audit.audit_log_entries TO audit_dashboard_ro, but
-- PostgreSQL requires USAGE on the schema before any table-level grant takes
-- effect. Without it, every query from AuditLogQueryRepository fails with
-- "permission denied for schema audit" despite the table-level SELECT.
GRANT USAGE ON SCHEMA audit TO audit_dashboard_ro;
