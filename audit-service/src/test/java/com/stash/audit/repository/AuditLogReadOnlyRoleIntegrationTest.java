package com.stash.audit.repository;

import com.stash.audit.api.dto.AuditLogQueryFilter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditLogReadOnlyRoleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("audit_db")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static DataSource migrationDataSource;
    static DataSource appRoleDataSource;
    static DataSource readOnlyDataSource;
    static AuditLogQueryRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        migrationDataSource = buildDataSource(POSTGRES.getJdbcUrl(), "postgres", "postgres");

        // CRITICAL ORDER: schema and roles MUST exist before Flyway runs.
        // V1 creates audit.audit_log_entries (schema must exist).
        // V2 does GRANT ... TO audit_dashboard_ro (role must exist).
        try (Connection conn = migrationDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA audit");
            stmt.execute("CREATE ROLE audit_app LOGIN PASSWORD 'app_pw'");
            stmt.execute("CREATE ROLE audit_dashboard_ro LOGIN PASSWORD 'ro_pw'");
        }

        Flyway.configure()
                .dataSource(migrationDataSource)
                .locations("classpath:db/migration/audit")
                .load()
                .migrate();

        // Infrastructure grants (not part of schema migration — done by ops at deploy time).
        try (Connection conn = migrationDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("GRANT USAGE ON SCHEMA audit TO audit_app");
            stmt.execute("GRANT INSERT, SELECT ON audit.audit_log_entries TO audit_app");
            stmt.execute("GRANT USAGE ON SCHEMA audit TO audit_dashboard_ro");
            // SELECT on audit_log_entries was already granted by V2 migration to audit_dashboard_ro
        }

        appRoleDataSource = buildDataSource(POSTGRES.getJdbcUrl(), "audit_app", "app_pw");
        readOnlyDataSource = buildDataSource(POSTGRES.getJdbcUrl(), "audit_dashboard_ro", "ro_pw");

        repository = new AuditLogQueryRepository(new JdbcTemplate(readOnlyDataSource));
        seedRows();
    }

    private static void seedRows() throws SQLException {
        try (Connection conn = appRoleDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (int i = 1; i <= 5; i++) {
                stmt.execute("""
                        INSERT INTO audit.audit_log_entries
                            (event_id, event_type, schema_version, source_service, actor_type, actor_id,
                             target_type, target_id, payload, occurred_at)
                        VALUES
                            ('evt-%d', 'PAYMENT_INITIATED', 1, 'payments', 'USER', gen_random_uuid(),
                             'WALLET', gen_random_uuid(), '{}', NOW() - INTERVAL '%d minutes')
                        """.formatted(i, i));
            }
        }
    }

    private static DataSource buildDataSource(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    @Test
    void queryReturnsNewestFirst() {
        var filter = new AuditLogQueryFilter(null, null, null, null, null, null, null);

        List<AuditLogEntryRow> rows = repository.query(filter, null, 10);

        assertThat(rows).hasSize(5);
        assertThat(rows.get(0).eventId()).isEqualTo("evt-1"); // smallest offset = most recent
        assertThat(rows.get(4).eventId()).isEqualTo("evt-5"); // largest offset = oldest
    }

    @Test
    void readOnlyRoleCantWrite() {
        var roJdbc = new JdbcTemplate(readOnlyDataSource);

        assertThatThrownBy(() -> roJdbc.execute(
                "INSERT INTO audit.audit_log_entries "
                + "(event_id, event_type, schema_version, source_service, actor_type, actor_id, "
                + "target_type, target_id, payload, occurred_at) "
                + "VALUES ('forbidden', 'X', 1, 'X', 'USER', gen_random_uuid(), 'WALLET', gen_random_uuid(), '{}', NOW())"))
                .isInstanceOf(Exception.class);
    }
}
