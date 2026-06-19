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
 * Verifies {@code user_module.password_reset_tokens} schema after Flyway V4 (v0.2-005).
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Password reset tokens migration (V4)")
class PasswordResetTokensMigrationTest {

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
                "SELECT to_regclass('user_module.password_reset_tokens')::text",
                String.class);
        assertThat(regclass).isEqualTo("user_module.password_reset_tokens");
    }

    @Test
    @DisplayName("columns match Schema doc §1.5 types and nullability")
    void columnsMatchSchema() {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name, udt_name, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'user_module'
                  AND table_name = 'password_reset_tokens'
                ORDER BY ordinal_position
                """);

        assertThat(columns).extracting(row -> row.get("column_name"))
                .containsExactly("id", "user_id", "token_hash", "expires_at", "consumed_at");

        assertColumn(columns, "id", "uuid", "NO");
        assertColumn(columns, "user_id", "uuid", "NO");
        assertColumn(columns, "token_hash", "varchar", "NO");
        assertColumn(columns, "expires_at", "timestamptz", "NO");
        assertColumn(columns, "consumed_at", "timestamptz", "YES");
    }

    @Test
    @DisplayName("has no created_at column")
    void noCreatedAtColumn() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'user_module'
                  AND table_name = 'password_reset_tokens'
                  AND column_name = 'created_at'
                """, Integer.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("partial index on (user_id, expires_at) WHERE consumed_at IS NULL exists")
    void partialActiveIndexExists() {
        List<String> indexes = jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'user_module'
                  AND tablename = 'password_reset_tokens'
                """, String.class);

        assertThat(indexes).contains("password_reset_tokens_active_idx");

        String indexDef = jdbc.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'user_module'
                  AND indexname = 'password_reset_tokens_active_idx'
                """, String.class);

        assertThat(indexDef).contains("user_id");
        assertThat(indexDef).contains("expires_at");
        assertThat(indexDef.toLowerCase()).contains("consumed_at is null");
    }

    @Test
    @DisplayName("unique constraint on token_hash is registered")
    void tokenHashUniqueConstraintExists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE table_schema = 'user_module'
                  AND table_name = 'password_reset_tokens'
                  AND constraint_name = 'password_reset_tokens_token_hash_unique'
                  AND constraint_type = 'UNIQUE'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("foreign key to user_module.users uses ON DELETE CASCADE")
    void foreignKeyUsesCascadeDelete() {
        Map<String, Object> fk = jdbc.queryForMap("""
                SELECT rc.delete_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.referential_constraints rc
                  ON rc.constraint_schema = tc.constraint_schema
                 AND rc.constraint_name = tc.constraint_name
                WHERE tc.table_schema = 'user_module'
                  AND tc.table_name = 'password_reset_tokens'
                  AND tc.constraint_name = 'password_reset_tokens_user_fk'
                  AND tc.constraint_type = 'FOREIGN KEY'
                """);

        assertThat(fk.get("delete_rule")).isEqualTo("CASCADE");
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
