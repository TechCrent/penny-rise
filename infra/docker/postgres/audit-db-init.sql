-- infra/docker/postgres/audit-db-init.sql
-- Runs once on fresh audit-db container startup (before Flyway).
--
-- Creates the two roles referenced in V2 migration:
--   audit_app          - application role (REVOKE UPDATE/DELETE target in V2)
--   audit_dashboard_ro - read-only dashboard user (GRANT SELECT target in V2)
--
-- In production these are provisioned by ops at the cluster level, not by
-- Flyway (which is per-database only). Locally Docker init fills that gap.

CREATE ROLE audit_app;

CREATE ROLE audit_dashboard_ro
    WITH LOGIN
    PASSWORD 'ro_local_pass'
    NOSUPERUSER NOCREATEDB NOCREATEROLE;

GRANT CONNECT ON DATABASE audit_db TO audit_dashboard_ro;
