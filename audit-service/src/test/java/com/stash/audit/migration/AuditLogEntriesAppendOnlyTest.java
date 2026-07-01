package com.stash.audit.migration;

import com.stash.audit.startup.AuditAppendOnlyGrantsCheck;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Testcontainers
class AuditLogEntriesAppendOnlyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("audit_db");

    static DataSource migrationDataSource; // superuser — runs Flyway, executes role setup
    static DataSource appRoleDataSource;   // restricted — what the app actually connects as

    @BeforeAll
    static void migrateAndProvisionRoles() throws Exception {
        migrationDataSource = DataSourceBuilder.create()
                .url(postgres.getJdbcUrl())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .build();

        // Create schema and roles BEFORE Flyway:
        // - V1 creates audit.audit_log_entries (schema must exist first)
        // - V2 GRANTs SELECT to audit_dashboard_ro (role must exist first)
        try (Connection conn = migrationDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA audit");
            stmt.execute("CREATE ROLE audit_app LOGIN PASSWORD 'test_app_password'");
            stmt.execute("CREATE ROLE audit_dashboard_ro");
        }

        // V1 creates the table; V2 REVOKEs UPDATE/DELETE from audit_app and
        // GRANTs SELECT to audit_dashboard_ro.
        Flyway.configure()
                .dataSource(migrationDataSource)
                .locations("classpath:db/migration/audit")
                .load()
                .migrate();

        // Grant the base privileges that infra provisions outside Flyway.
        // V2 only REVOKEs — the initial INSERT/SELECT grant is an infra/DBA
        // concern that Flyway intentionally doesn't own.
        try (Connection conn = migrationDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("GRANT USAGE ON SCHEMA audit TO audit_app");
            stmt.execute("GRANT INSERT, SELECT ON audit.audit_log_entries TO audit_app");
        }

        appRoleDataSource = DataSourceBuilder.create()
                .url(postgres.getJdbcUrl())
                .username("audit_app")
                .password("test_app_password")
                .build();
    }

    @Test
    @DisplayName("UPDATE from the application role fails with permission denied")
    void updateDeniedForAppRole() throws SQLException {
        try (Connection conn = appRoleDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThatThrownBy(() ->
                    stmt.execute("UPDATE audit.audit_log_entries SET event_type = 'x' WHERE event_id = 'nonexistent'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    @DisplayName("DELETE from the application role fails with permission denied")
    void deleteDeniedForAppRole() throws SQLException {
        try (Connection conn = appRoleDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThatThrownBy(() ->
                    stmt.execute("DELETE FROM audit.audit_log_entries WHERE event_id = 'nonexistent'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    @DisplayName("INSERT and SELECT still work for the application role — REVOKE didn't overreach")
    void insertAndSelectStillWork() throws SQLException {
        try (Connection conn = appRoleDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                INSERT INTO audit.audit_log_entries
                    (event_id, event_type, source_service, actor_type, target_type, payload, occurred_at)
                VALUES
                    ('test-evt-1', 'TestEvent', 'test', 'SYSTEM', 'UNKNOWN', '{}'::jsonb, now())
                """);
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM audit.audit_log_entries WHERE event_id = 'test-evt-1'");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("startup check fails fast when grants are deliberately misconfigured")
    void startupCheckFailsFastOnMisconfiguredGrants() throws SQLException {
        // Temporarily grant UPDATE to simulate permission drift
        try (Connection conn = migrationDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("GRANT UPDATE ON audit.audit_log_entries TO audit_app");
        }

        try {
            // Use the superuser datasource so information_schema.role_table_grants
            // can see grants for both checked roles (audit_app and audit_dashboard_ro)
            JdbcTemplate template = new JdbcTemplate(migrationDataSource);
            var check = new AuditAppendOnlyGrantsCheck(template, "audit_app", "audit_dashboard_ro");

            assertThatThrownBy(() -> check.run(mock(ApplicationArguments.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("UPDATE");
        } finally {
            // Restore correct state regardless of assertion outcome
            try (Connection conn = migrationDataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("REVOKE UPDATE ON audit.audit_log_entries FROM audit_app");
            }
        }
    }
}
