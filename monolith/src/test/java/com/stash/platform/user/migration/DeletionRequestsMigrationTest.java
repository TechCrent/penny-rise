package com.stash.platform.user.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@code user_module.deletion_requests} schema after Flyway V5 (v0.2-006).
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Deletion requests migration (V5)")
class DeletionRequestsMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("monolith_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("table exists in user_module schema")
    void tableExists() {
        String regclass = jdbc.queryForObject(
                "SELECT to_regclass('user_module.deletion_requests')::text",
                String.class);
        assertThat(regclass).isEqualTo("user_module.deletion_requests");
    }

    @Test
    @DisplayName("columns match Schema doc §1.4 types and nullability")
    void columnsMatchSchema() {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name, udt_name, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'user_module'
                  AND table_name = 'deletion_requests'
                ORDER BY ordinal_position
                """);

        assertThat(columns).extracting(row -> row.get("column_name"))
                .containsExactly(
                        "id",
                        "user_id",
                        "status",
                        "blockers_at_submission",
                        "submitted_at",
                        "scheduled_completion_at",
                        "completed_at",
                        "cancelled_at");

        assertColumn(columns, "id", "uuid", "NO");
        assertColumn(columns, "user_id", "uuid", "NO");
        assertColumn(columns, "status", "varchar", "NO");
        assertColumn(columns, "blockers_at_submission", "jsonb", "YES");
        assertColumn(columns, "submitted_at", "timestamptz", "NO");
        assertColumn(columns, "scheduled_completion_at", "timestamptz", "NO");
        assertColumn(columns, "completed_at", "timestamptz", "YES");
        assertColumn(columns, "cancelled_at", "timestamptz", "YES");
    }

    @Test
    @DisplayName("blockers_at_submission is JSONB")
    void blockersColumnIsJsonb() {
        String udtName = jdbc.queryForObject("""
                SELECT udt_name
                FROM information_schema.columns
                WHERE table_schema = 'user_module'
                  AND table_name = 'deletion_requests'
                  AND column_name = 'blockers_at_submission'
                """, String.class);
        assertThat(udtName).isEqualTo("jsonb");
    }

    @Test
    @DisplayName("status CHECK constraint allows PENDING, COMPLETED, CANCELLED")
    void statusCheckConstraintDefinition() {
        String definition = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(c.oid)
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'user_module'
                  AND t.relname = 'deletion_requests'
                  AND c.conname = 'deletion_requests_status_check'
                """, String.class);

        assertThat(definition).contains("PENDING");
        assertThat(definition).contains("COMPLETED");
        assertThat(definition).contains("CANCELLED");
    }

    @Test
    @DisplayName("partial cleanup index on scheduled_completion_at WHERE status = PENDING exists")
    void cleanupPartialIndexExists() {
        String indexDef = jdbc.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'user_module'
                  AND indexname = 'deletion_requests_cleanup_job_idx'
                """, String.class);

        assertThat(indexDef).contains("scheduled_completion_at");
        assertThat(indexDef.toLowerCase()).contains("'pending'");
    }

    @Test
    @DisplayName("supporting index on user_id exists")
    void userIdIndexExists() {
        List<String> indexes = jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'user_module'
                  AND tablename = 'deletion_requests'
                """, String.class);

        assertThat(indexes).contains("deletion_requests_user_id_idx");
    }

    @Test
    @DisplayName("foreign key to user_module.users uses ON DELETE RESTRICT")
    void foreignKeyUsesRestrictDelete() {
        Map<String, Object> fk = jdbc.queryForMap("""
                SELECT rc.delete_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.referential_constraints rc
                  ON rc.constraint_schema = tc.constraint_schema
                 AND rc.constraint_name = tc.constraint_name
                WHERE tc.table_schema = 'user_module'
                  AND tc.table_name = 'deletion_requests'
                  AND tc.constraint_name = 'deletion_requests_user_fk'
                  AND tc.constraint_type = 'FOREIGN KEY'
                """);

        assertThat(fk.get("delete_rule")).isEqualTo("RESTRICT");
    }

    @Test
    @DisplayName("partial EXCLUDE constraint one pending per user is registered")
    void excludeOnePendingPerUserExists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'user_module'
                  AND t.relname = 'deletion_requests'
                  AND c.conname = 'deletion_requests_one_pending_per_user'
                  AND c.contype = 'x'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }

    private static void assertColumn(
            List<Map<String, Object>> columns,
            String name,
            String udtName,
            String nullable) {
        Map<String, Object> column = columns.stream()
                .filter(row -> name.equals(row.get("column_name")))
                .collect(Collectors.toMap(
                        row -> row.get("column_name").toString(),
                        row -> row,
                        (a, b) -> a))
                .get(name);

        assertThat(column).isNotNull();
        assertThat(column.get("udt_name")).isEqualTo(udtName);
        assertThat(column.get("is_nullable")).isEqualTo(nullable);
    }
}
