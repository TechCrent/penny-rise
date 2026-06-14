-- infra/docker/postgres/init.sql
-- Runs on first container start only.
-- Creates one database per service and grants the app user access.

CREATE DATABASE monolith_db;
CREATE DATABASE payments_db;
CREATE DATABASE kyc_db;
CREATE DATABASE audit_db;

GRANT ALL PRIVILEGES ON DATABASE monolith_db TO :app_user;
GRANT ALL PRIVILEGES ON DATABASE payments_db TO :app_user;
GRANT ALL PRIVILEGES ON DATABASE kyc_db TO :app_user;
GRANT ALL PRIVILEGES ON DATABASE audit_db TO :app_user;