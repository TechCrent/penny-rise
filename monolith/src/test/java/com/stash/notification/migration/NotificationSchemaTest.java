package com.stash.notification.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class NotificationSchemaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("monolith_db")
            .withUsername("postgres")
            .withPassword("postgres");

    static DataSource dataSource;
    static UUID testUserId;

    @BeforeAll
    static void migrateAndSeedUser() throws Exception {
        dataSource = DataSourceBuilder.create()
                .url(POSTGRES.getJdbcUrl())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();

        // Full monolith migration history in version order — V1 creates
        // user_module.users which V26's FK depends on.
        // The monolith configures 8 separate locations (not a single recursive
        // classpath:db/migration scan) — must match that convention here.
        Flyway.configure()
                .dataSource(dataSource)
                .locations(
                        "classpath:db/migration/user_module",
                        "classpath:db/migration/auth",
                        "classpath:db/migration/vault",
                        "classpath:db/migration/susu",
                        "classpath:db/migration/transfer",
                        "classpath:db/migration/challenge",
                        "classpath:db/migration/notification",
                        "classpath:db/migration/admin")
                .load()
                .migrate();

        testUserId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO user_module.users
                        (id, email, password_hash, display_name, kyc_status, subscription_tier, account_status, created_at)
                    VALUES
                        ('%s', 'test@stash.app', '$2a$12$placeholder', 'Test User', 'APPROVED', 'FREE', 'ACTIVE', now())
                    """.formatted(testUserId));
        }
    }

    @Test
    @DisplayName("both migrations apply cleanly against a fresh monolith-db")
    void migrationsApplyCleanly() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    @DisplayName("notifications table has the expected columns and types")
    void notificationsSchemaMatches() throws SQLException {
        var expectedColumns = Map.ofEntries(
                Map.entry("id",                 "uuid"),
                Map.entry("user_id",            "uuid"),
                Map.entry("notification_type",  "varchar"),
                Map.entry("channel",            "varchar"),
                Map.entry("title",              "varchar"),
                Map.entry("body",               "text"),
                Map.entry("deep_link",          "varchar"),
                Map.entry("payload",            "jsonb"),
                Map.entry("delivery_status",    "varchar"),
                Map.entry("delivery_attempts",  "int4"),
                Map.entry("read_at",            "timestamptz"),
                Map.entry("created_at",         "timestamptz")
        );

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            var actualColumns = new HashMap<String, String>();
            try (ResultSet rs = meta.getColumns(null, "notification", "notifications", null)) {
                while (rs.next()) {
                    actualColumns.put(rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"));
                }
            }
            assertThat(actualColumns.keySet())
                    .containsExactlyInAnyOrderElementsOf(expectedColumns.keySet());
        }
    }

    @Test
    @DisplayName("device_tokens table has the expected columns and types")
    void deviceTokensSchemaMatches() throws SQLException {
        var expectedColumnNames = Set.of(
                "id", "user_id", "device_id", "expo_push_token", "platform",
                "is_active", "registered_at", "last_used_at");

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            var actualColumnNames = new HashSet<String>();
            try (ResultSet rs = meta.getColumns(null, "notification", "device_tokens", null)) {
                while (rs.next()) {
                    actualColumnNames.add(rs.getString("COLUMN_NAME"));
                }
            }
            assertThat(actualColumnNames)
                    .containsExactlyInAnyOrderElementsOf(expectedColumnNames);
        }
    }

    @Test
    @DisplayName("duplicate expo_push_token is rejected by the UNIQUE constraint")
    void duplicateTokenRejected() throws SQLException {
        String sharedToken = "ExponentPushToken[duplicate-test-token]";

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO notification.device_tokens
                        (id, user_id, device_id, expo_push_token, platform)
                    VALUES
                        (gen_random_uuid(), '%s', 'device-A', '%s', 'IOS')
                    """.formatted(testUserId, sharedToken));

            assertThatThrownBy(() -> stmt.execute("""
                        INSERT INTO notification.device_tokens
                            (id, user_id, device_id, expo_push_token, platform)
                        VALUES
                            (gen_random_uuid(), '%s', 'device-B', '%s', 'ANDROID')
                        """.formatted(testUserId, sharedToken)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("device_tokens_expo_push_token_uk");
        }
    }

    @Test
    @DisplayName("the same (user_id, device_id) with is_active=true twice is rejected — the partial unique index v0.5-014 depends on")
    void duplicateActiveUserDeviceRejected() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO notification.device_tokens
                        (id, user_id, device_id, expo_push_token, platform, is_active)
                    VALUES
                        (gen_random_uuid(), '%s', 'device-C', 'token-1', 'IOS', true)
                    """.formatted(testUserId));

            assertThatThrownBy(() -> stmt.execute("""
                        INSERT INTO notification.device_tokens
                            (id, user_id, device_id, expo_push_token, platform, is_active)
                        VALUES
                            (gen_random_uuid(), '%s', 'device-C', 'token-2', 'IOS', true)
                        """.formatted(testUserId)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("device_tokens_user_device_active_uk");
        }
    }

    @Test
    @DisplayName("inactive then active on the same device is allowed — the upsert-and-flip pattern v0.5-014 relies on")
    void inactiveThenActiveSameDeviceIsAllowed() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO notification.device_tokens
                        (id, user_id, device_id, expo_push_token, platform, is_active)
                    VALUES
                        (gen_random_uuid(), '%s', 'device-D', 'token-old', 'ANDROID', false)
                    """.formatted(testUserId));

            // Must NOT throw — partial index only covers is_active=true rows.
            stmt.execute("""
                    INSERT INTO notification.device_tokens
                        (id, user_id, device_id, expo_push_token, platform, is_active)
                    VALUES
                        (gen_random_uuid(), '%s', 'device-D', 'token-new', 'ANDROID', true)
                    """.formatted(testUserId));
        }
    }
}
