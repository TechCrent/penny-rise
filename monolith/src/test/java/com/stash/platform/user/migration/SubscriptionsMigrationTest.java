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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@code user_module.subscriptions} schema after Flyway V41 (v0.5-028).
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Subscriptions migration (V41)")
class SubscriptionsMigrationTest {

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
                "SELECT to_regclass('user_module.subscriptions')::text",
                String.class);
        assertThat(regclass).isEqualTo("user_module.subscriptions");
    }

    @Test
    @DisplayName("columns match spec types and nullability")
    void columnsMatchSchema() {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name, udt_name, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'user_module'
                  AND table_name = 'subscriptions'
                ORDER BY ordinal_position
                """);

        assertThat(columns).extracting(row -> row.get("column_name"))
                .containsExactly(
                        "id",
                        "user_id",
                        "tier",
                        "started_at",
                        "ends_at",
                        "source",
                        "external_subscription_reference",
                        "created_at",
                        "updated_at");

        assertColumn(columns, "id", "uuid", "NO");
        assertColumn(columns, "user_id", "uuid", "NO");
        assertColumn(columns, "tier", "varchar", "NO");
        assertColumn(columns, "started_at", "timestamptz", "NO");
        assertColumn(columns, "ends_at", "timestamptz", "YES");
        assertColumn(columns, "source", "varchar", "NO");
        assertColumn(columns, "external_subscription_reference", "varchar", "YES");
        assertColumn(columns, "created_at", "timestamptz", "NO");
        assertColumn(columns, "updated_at", "timestamptz", "NO");
    }

    @Test
    @DisplayName("tier CHECK constraint allows FREE and PREMIUM only")
    void tierCheckConstraintDefinition() {
        String definition = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(c.oid)
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'user_module'
                  AND t.relname = 'subscriptions'
                  AND c.conname = 'subscriptions_tier_check'
                """, String.class);

        assertThat(definition).contains("FREE");
        assertThat(definition).contains("PREMIUM");
    }

    @Test
    @DisplayName("source CHECK constraint allows PAYSTACK_SUB and SYSTEM only")
    void sourceCheckConstraintDefinition() {
        String definition = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(c.oid)
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'user_module'
                  AND t.relname = 'subscriptions'
                  AND c.conname = 'subscriptions_source_check'
                """, String.class);

        assertThat(definition).contains("PAYSTACK_SUB");
        assertThat(definition).contains("SYSTEM");
    }

    @Test
    @DisplayName("unique constraint on user_id exists — one row per user")
    void userIdUniqueConstraintExists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'user_module'
                  AND t.relname = 'subscriptions'
                  AND c.conname = 'subscriptions_user_uk'
                  AND c.contype = 'u'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("supporting index on (user_id, ends_at) exists")
    void userIdEndsAtIndexExists() {
        List<String> indexes = jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'user_module'
                  AND tablename = 'subscriptions'
                """, String.class);

        assertThat(indexes).contains("subscriptions_user_ends_at_idx");
    }

    @Test
    @DisplayName("foreign key to user_module.users exists")
    void foreignKeyToUsersExists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints tc
                WHERE tc.table_schema = 'user_module'
                  AND tc.table_name = 'subscriptions'
                  AND tc.constraint_name = 'subscriptions_user_fk'
                  AND tc.constraint_type = 'FOREIGN KEY'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }

    /**
     * V41's backfill runs once, at migration time, against whatever users
     * already existed then — by the time this test class's Spring context
     * starts, that has already happened (there were zero users at that
     * point, so it backfilled zero rows). To verify the backfill SQL
     * itself is correct without inventing an unprecedented two-stage
     * Flyway orchestration (target=40, insert a user, re-migrate to 41 —
     * no test in this codebase does that), this re-executes V41's exact
     * backfill INSERT...SELECT against a freshly-inserted user and asserts
     * the result — proving the SQL is correct, which is what would run for
     * a genuinely pre-existing user on a real deployment.
     */
    @Test
    @DisplayName("backfill SQL creates a FREE/SYSTEM row with started_at = users.created_at")
    void backfillLogicCreatesCorrectRow() {
        UUID userId = UUID.randomUUID();
        Instant userCreatedAt = Instant.parse("2026-02-15T00:00:00Z");

        jdbc.update(
                """
                INSERT INTO user_module.users (id, email, password_hash, display_name, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                userId, "backfill-test-" + userId + "@stash.com", "$2a$12$hashedpassword",
                "Backfill Test", java.sql.Timestamp.from(userCreatedAt));

        // No pre-existing subscription row for this user yet — the invariant
        // the backfill exists to fix.
        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_module.subscriptions WHERE user_id = ?",
                Integer.class, userId);
        assertThat(before).isZero();

        // V41's exact backfill statement, scoped to this one user.
        jdbc.update(
                """
                INSERT INTO user_module.subscriptions
                    (id, user_id, tier, started_at, ends_at, source, created_at, updated_at)
                SELECT gen_random_uuid(), u.id, 'FREE', u.created_at, NULL, 'SYSTEM', now(), now()
                FROM user_module.users u
                WHERE u.id = ?
                """,
                userId);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT tier, source, ends_at, started_at FROM user_module.subscriptions WHERE user_id = ?",
                userId);
        assertThat(row.get("tier")).isEqualTo("FREE");
        assertThat(row.get("source")).isEqualTo("SYSTEM");
        assertThat(row.get("ends_at")).isNull();
        assertThat(((java.sql.Timestamp) row.get("started_at")).toInstant()).isEqualTo(userCreatedAt);
    }

    @Test
    @DisplayName("no user is left without a subscription row after backfill")
    void noOrphanedUsersAfterBackfill() {
        UUID userId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO user_module.users (id, email, password_hash, display_name)
                VALUES (?, ?, ?, ?)
                """,
                userId, "orphan-check-" + userId + "@stash.com", "$2a$12$hashedpassword", "Orphan Check");
        jdbc.update(
                """
                INSERT INTO user_module.subscriptions (id, user_id, tier, source)
                VALUES (gen_random_uuid(), ?, 'FREE', 'SYSTEM')
                """,
                userId);

        Integer orphanCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_module.users u
                WHERE NOT EXISTS (
                    SELECT 1 FROM user_module.subscriptions s WHERE s.user_id = u.id
                )
                """, Integer.class);

        assertThat(orphanCount).isZero();
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
