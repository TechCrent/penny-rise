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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies behavioral constraints on {@code user_module.email_verification_tokens}.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Email verification tokens constraints")
class EmailVerificationTokensConstraintsTest {

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
    private static final UUID TOKEN_ID_1 = UUID.fromString("018f3a2b-3c4d-7e8f-9a0b-1c2d3e4f5001");
    private static final UUID TOKEN_ID_2 = UUID.fromString("018f3a2b-3c4d-7e8f-9a0b-1c2d3e4f5002");

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM user_module.email_verification_tokens");
        jdbc.update("DELETE FROM user_module.users");
    }

    @Test
    @DisplayName("insert with non-existent user_id fails (foreign key)")
    void insertWithUnknownUserFails() {
        assertThatThrownBy(() -> insertToken(
                TOKEN_ID_1,
                UUID.randomUUID(),
                "hash-unknown-user",
                null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("duplicate token_hash fails (unique constraint)")
    void duplicateTokenHashFails() {
        insertUser(USER_ID);
        insertToken(TOKEN_ID_1, USER_ID, "duplicate-hash", null);

        assertThatThrownBy(() -> insertToken(
                TOKEN_ID_2,
                USER_ID,
                "duplicate-hash",
                null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("insert active token with consumed_at NULL succeeds")
    void insertActiveTokenSucceeds() {
        insertUser(USER_ID);
        insertToken(TOKEN_ID_1, USER_ID, "active-hash", null);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_module.email_verification_tokens WHERE id = ?",
                Integer.class,
                TOKEN_ID_1);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("second token for same user with different token_hash succeeds")
    void secondTokenSameUserSucceeds() {
        insertUser(USER_ID);
        insertToken(TOKEN_ID_1, USER_ID, "hash-one", null);
        insertToken(TOKEN_ID_2, USER_ID, "hash-two", null);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_module.email_verification_tokens WHERE user_id = ?",
                Integer.class,
                USER_ID);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("deleting user cascades and removes verification tokens")
    void deleteUserCascadesTokens() {
        insertUser(USER_ID);
        insertToken(TOKEN_ID_1, USER_ID, "cascade-hash", null);

        jdbc.update("DELETE FROM user_module.users WHERE id = ?", USER_ID);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_module.email_verification_tokens WHERE user_id = ?",
                Integer.class,
                USER_ID);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("updating consumed_at on existing row succeeds")
    void updateConsumedAtSucceeds() {
        insertUser(USER_ID);
        insertToken(TOKEN_ID_1, USER_ID, "consume-hash", null);

        Instant consumedAt = Instant.parse("2026-06-19T12:00:00Z");
        int updated = jdbc.update(
                """
                UPDATE user_module.email_verification_tokens
                   SET consumed_at = ?
                 WHERE id = ?
                """,
                java.sql.Timestamp.from(consumedAt),
                TOKEN_ID_1);

        assertThat(updated).isEqualTo(1);

        Instant stored = jdbc.queryForObject(
                "SELECT consumed_at FROM user_module.email_verification_tokens WHERE id = ?",
                Instant.class,
                TOKEN_ID_1);
        assertThat(stored).isEqualTo(consumedAt);
    }

    private void insertUser(UUID userId) {
        jdbc.update(
                """
                INSERT INTO user_module.users (id, email, password_hash, display_name)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                "verify-test@stash.com",
                "$2a$12$hashedpassword",
                "Verify Test");
    }

    private void insertToken(UUID id, UUID userId, String tokenHash, Instant consumedAt) {
        jdbc.update(
                """
                INSERT INTO user_module.email_verification_tokens
                    (id, user_id, token_hash, expires_at, consumed_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                userId,
                tokenHash,
                java.sql.Timestamp.from(Instant.parse("2026-06-20T12:00:00Z")),
                consumedAt == null ? null : java.sql.Timestamp.from(consumedAt));
    }
}
