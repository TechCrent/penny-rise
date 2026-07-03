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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies behavioral constraints on {@code user_module.subscriptions}.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Subscriptions constraints")
class SubscriptionsConstraintsTest {

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

    private static final UUID USER_ID = UUID.fromString("018f3a2b-3c4d-7e8f-9a0b-1c2d3e4f6abc");

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM user_module.subscriptions");
        jdbc.update("DELETE FROM user_module.users");
    }

    @Test
    @DisplayName("insert with non-existent user_id fails (foreign key)")
    void insertWithUnknownUserFails() {
        assertThatThrownBy(() -> insertFreeSubscription(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("insert valid FREE subscription succeeds")
    void insertValidFreeSubscriptionSucceeds() {
        insertUser(USER_ID);
        insertFreeSubscription(UUID.randomUUID(), USER_ID);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_module.subscriptions WHERE user_id = ?",
                Integer.class,
                USER_ID);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("second subscription row for the same user fails (UNIQUE)")
    void secondSubscriptionForSameUserFails() {
        insertUser(USER_ID);
        insertFreeSubscription(UUID.randomUUID(), USER_ID);

        assertThatThrownBy(() -> insertFreeSubscription(UUID.randomUUID(), USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("insert with invalid tier fails (CHECK constraint)")
    void invalidTierFails() {
        insertUser(USER_ID);

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO user_module.subscriptions (id, user_id, tier, source)
                VALUES (?, ?, ?, ?)
                """,
                UUID.randomUUID(), USER_ID, "GOLD", "SYSTEM"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("insert with invalid source fails (CHECK constraint)")
    void invalidSourceFails() {
        insertUser(USER_ID);

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO user_module.subscriptions (id, user_id, tier, source)
                VALUES (?, ?, ?, ?)
                """,
                UUID.randomUUID(), USER_ID, "FREE", "APPLE_STORE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a FREE row with a non-null ends_at fails (CHECK constraint)")
    void freeTierWithEndsAtFails() {
        insertUser(USER_ID);

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO user_module.subscriptions (id, user_id, tier, ends_at, source)
                VALUES (?, ?, 'FREE', NOW() + INTERVAL '30 days', 'SYSTEM')
                """,
                UUID.randomUUID(), USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a PREMIUM row with an ends_at succeeds")
    void premiumTierWithEndsAtSucceeds() {
        insertUser(USER_ID);

        int inserted = jdbc.update(
                """
                INSERT INTO user_module.subscriptions
                    (id, user_id, tier, ends_at, source, external_subscription_reference)
                VALUES (?, ?, 'PREMIUM', NOW() + INTERVAL '30 days', 'PAYSTACK_SUB', 'SUB_CODE_123')
                """,
                UUID.randomUUID(), USER_ID);

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("upgrade updates the existing row in place — no history table, by design")
    void upgradeUpdatesRowInPlace() {
        insertUser(USER_ID);
        insertFreeSubscription(UUID.randomUUID(), USER_ID);

        int updated = jdbc.update(
                """
                UPDATE user_module.subscriptions
                   SET tier = 'PREMIUM',
                       ends_at = NOW() + INTERVAL '30 days',
                       source = 'PAYSTACK_SUB',
                       external_subscription_reference = 'SUB_CODE_456',
                       updated_at = NOW()
                 WHERE user_id = ?
                """,
                USER_ID);
        assertThat(updated).isEqualTo(1);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_module.subscriptions WHERE user_id = ?",
                Integer.class,
                USER_ID);
        assertThat(count).isEqualTo(1); // still exactly one row — updated, not appended

        String tier = jdbc.queryForObject(
                "SELECT tier FROM user_module.subscriptions WHERE user_id = ?",
                String.class,
                USER_ID);
        assertThat(tier).isEqualTo("PREMIUM");
    }

    @Test
    @DisplayName("deleting a user with a subscription row fails (default FK delete behaviour)")
    void deleteUserWithSubscriptionFails() {
        insertUser(USER_ID);
        insertFreeSubscription(UUID.randomUUID(), USER_ID);

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM user_module.users WHERE id = ?", USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(UUID userId) {
        jdbc.update(
                """
                INSERT INTO user_module.users (id, email, password_hash, display_name)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                "subscriptions-test-" + userId + "@stash.com",
                "$2a$12$hashedpassword",
                "Subscriptions Test");
    }

    private void insertFreeSubscription(UUID id, UUID userId) {
        jdbc.update(
                """
                INSERT INTO user_module.subscriptions (id, user_id, tier, source)
                VALUES (?, ?, 'FREE', 'SYSTEM')
                """,
                id, userId);
    }
}
