package com.stash.platform.user.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies behavioral constraints on {@code user_module.deletion_requests}.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Deletion requests constraints")
class DeletionRequestsConstraintsTest {

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

    private static final UUID USER_ID = UUID.fromString("018f3a2b-3c4d-7e8f-9a0b-1c2d3e4f5abc");
    private static final UUID REQUEST_ID_1 = UUID.fromString("018f3a2b-3c4d-7e8f-9a0b-1c2d3e4f5001");
    private static final UUID REQUEST_ID_2 = UUID.fromString("018f3a2b-3c4d-7e8f-9a0b-1c2d3e4f5002");

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM user_module.deletion_requests");
        jdbc.update("DELETE FROM user_module.users");
    }

    @Test
    @DisplayName("insert with non-existent user_id fails (foreign key)")
    void insertWithUnknownUserFails() {
        assertThatThrownBy(() -> insertPendingRequest(
                REQUEST_ID_1,
                UUID.randomUUID(),
                Instant.parse("2026-07-19T12:00:00Z"),
                "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("insert valid PENDING request with empty JSONB blockers succeeds")
    void insertValidPendingRequestSucceeds() {
        insertUser(USER_ID);
        insertPendingRequest(
                REQUEST_ID_1,
                USER_ID,
                Instant.parse("2026-07-19T12:00:00Z"),
                "{}");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_module.deletion_requests WHERE id = ?",
                Integer.class,
                REQUEST_ID_1);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("second PENDING request for same user fails (partial EXCLUDE)")
    void secondPendingRequestSameUserFails() {
        insertUser(USER_ID);
        insertPendingRequest(
                REQUEST_ID_1,
                USER_ID,
                Instant.parse("2026-07-19T12:00:00Z"),
                "{}");

        assertThatThrownBy(() -> insertPendingRequest(
                REQUEST_ID_2,
                USER_ID,
                Instant.parse("2026-08-19T12:00:00Z"),
                "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("insert with invalid status fails (CHECK constraint)")
    void invalidStatusFails() {
        insertUser(USER_ID);

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO user_module.deletion_requests
                    (id, user_id, status, scheduled_completion_at)
                VALUES (?, ?, ?, ?)
                """,
                REQUEST_ID_1,
                USER_ID,
                "INVALID",
                java.sql.Timestamp.from(Instant.parse("2026-07-19T12:00:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleting user with PENDING request fails (ON DELETE RESTRICT)")
    void deleteUserWithPendingRequestFails() {
        insertUser(USER_ID);
        insertPendingRequest(
                REQUEST_ID_1,
                USER_ID,
                Instant.parse("2026-07-19T12:00:00Z"),
                "{}");

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM user_module.users WHERE id = ?", USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("cancelled request allows new PENDING request for same user")
    void cancelledThenNewPendingSucceeds() {
        insertUser(USER_ID);
        insertPendingRequest(
                REQUEST_ID_1,
                USER_ID,
                Instant.parse("2026-07-19T12:00:00Z"),
                "{}");

        int cancelled = jdbc.update(
                """
                UPDATE user_module.deletion_requests
                   SET status = 'CANCELLED',
                       cancelled_at = NOW()
                 WHERE id = ?
                """,
                REQUEST_ID_1);
        assertThat(cancelled).isEqualTo(1);

        insertPendingRequest(
                REQUEST_ID_2,
                USER_ID,
                Instant.parse("2026-08-19T12:00:00Z"),
                "{}");

        Integer pendingCount = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM user_module.deletion_requests
                WHERE user_id = ? AND status = 'PENDING'
                """,
                Integer.class,
                USER_ID);
        assertThat(pendingCount).isEqualTo(1);
    }

    @Test
    @DisplayName("cleanup partial index has WHERE status = PENDING predicate")
    void cleanupIndexPredicateInPgIndexes() {
        String indexDef = jdbc.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'user_module'
                  AND indexname = 'deletion_requests_cleanup_job_idx'
                """, String.class);

        assertThat(indexDef.toLowerCase()).contains("'pending'");
        assertThat(indexDef).contains("scheduled_completion_at");
    }

    @Test
    @DisplayName("v0.5 cleanup query uses deletion_requests_cleanup_job_idx")
    void cleanupQueryUsesPartialIndex() {
        // Drop EXCLUDE index so the planner cannot pick it over the cleanup partial index.
        // Rolled back automatically after the test (@Transactional DataJpaTest).
        jdbc.execute(
                "ALTER TABLE user_module.deletion_requests "
                        + "DROP CONSTRAINT deletion_requests_one_pending_per_user");

        insertUser(USER_ID);
        insertPendingRequest(
                REQUEST_ID_1,
                USER_ID,
                Instant.now().minusSeconds(86_400),
                "{}");

        jdbc.update(
                """
                INSERT INTO user_module.deletion_requests
                    (id, user_id, status, scheduled_completion_at, completed_at)
                VALUES (?, ?, 'COMPLETED', NOW() - INTERVAL '5 days', NOW() - INTERVAL '1 day')
                """,
                UUID.fromString("018f3a2b-3c4d-7e8f-9a0b-1c2d3e4f5003"),
                USER_ID);

        jdbc.execute("ANALYZE user_module.deletion_requests");
        jdbc.execute("SET LOCAL enable_seqscan = off");

        List<String> plan = jdbc.query(
                """
                EXPLAIN (FORMAT TEXT)
                SELECT id FROM user_module.deletion_requests
                WHERE status = 'PENDING' AND scheduled_completion_at <= NOW()
                """,
                (rs, rowNum) -> rs.getString(1));

        assertThat(String.join("\n", plan)).contains("deletion_requests_cleanup_job_idx");
    }

    private void insertUser(UUID userId) {
        insertUser(userId, "deletion-test@stash.com");
    }

    private void insertUser(UUID userId, String email) {
        jdbc.update(
                """
                INSERT INTO user_module.users (id, email, password_hash, display_name)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                email,
                "$2a$12$hashedpassword",
                "Deletion Test");
    }

    private void insertPendingRequest(
            UUID id,
            UUID userId,
            Instant scheduledCompletionAt,
            String blockersJson) {
        jdbc.update(
                """
                INSERT INTO user_module.deletion_requests
                    (id, user_id, status, blockers_at_submission, scheduled_completion_at)
                VALUES (?, ?, 'PENDING', ?::jsonb, ?)
                """,
                id,
                userId,
                blockersJson,
                java.sql.Timestamp.from(scheduledCompletionAt));
    }
}
