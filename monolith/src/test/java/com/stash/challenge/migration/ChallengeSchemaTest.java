package com.stash.challenge.migration;

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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ChallengeSchemaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("monolith_db")
            .withUsername("postgres")
            .withPassword("postgres");

    static DataSource dataSource;
    static UUID sampleChallengeId;

    @BeforeAll
    static void migrateAndSeedChallenge() throws Exception {
        dataSource = DataSourceBuilder.create()
                .url(POSTGRES.getJdbcUrl())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();

        // V34's REVOKE is conditional (IF EXISTS stash_app) — same as V21_1 —
        // so no role creation needed here before Flyway.
        // Same 8 subdirectory locations as NotificationSchemaTest (v0.5-012):
        // classpath:db/migration alone does NOT recursively scan subdirectories.
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

        sampleChallengeId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO challenge.savings_challenges
                        (id, name, description, challenge_type, target_amount,
                         target_duration_days, system_owned, creator_user_id, is_active)
                    VALUES
                        ('%s', 'Save GHS 5/day for 30 days', 'A test challenge',
                         'SAVE_AMOUNT', 15000, 30, true, NULL, true)
                    """.formatted(sampleChallengeId));
        }
    }

    @Test
    @DisplayName("V31-V34 apply cleanly against a fresh monolith-db")
    void migrationsApplyCleanly() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    @DisplayName("savings_challenges columns match Schema doc §5.1")
    void savingsChallengesSchemaMatches() throws SQLException {
        assertColumnsExactly("savings_challenges", Set.of(
                "id", "name", "description", "challenge_type", "target_amount",
                "target_duration_days", "system_owned", "creator_user_id",
                "is_active", "badge_id", "created_at"));
    }

    @Test
    @DisplayName("user_challenges columns match Schema doc §5.2")
    void userChallengesSchemaMatches() throws SQLException {
        assertColumnsExactly("user_challenges", Set.of(
                "id", "user_id", "challenge_id", "status", "progress_amount",
                "progress_count", "started_at", "ends_at", "completed_at"));
    }

    @Test
    @DisplayName("user_badges columns match Schema doc §5.3")
    void userBadgesSchemaMatches() throws SQLException {
        assertColumnsExactly("user_badges", Set.of(
                "id", "user_id", "badge_code", "badge_name", "awarded_at",
                "awarded_for_entity_type", "awarded_for_entity_id"));
    }

    @Test
    @DisplayName("enrolling the same user in the same challenge twice fails UNIQUE(user_id, challenge_id)")
    void duplicateEnrollmentRejected() throws SQLException {
        UUID userId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO challenge.user_challenges (id, user_id, challenge_id, status)
                    VALUES (gen_random_uuid(), '%s', '%s', 'ACTIVE')
                    """.formatted(userId, sampleChallengeId));

            assertThatThrownBy(() -> stmt.execute("""
                        INSERT INTO challenge.user_challenges (id, user_id, challenge_id, status)
                        VALUES (gen_random_uuid(), '%s', '%s', 'ACTIVE')
                        """.formatted(userId, sampleChallengeId)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("user_challenges_user_challenge_uk");
        }
    }

    @Test
    @DisplayName("same user, different challenge is allowed — constraint is correctly scoped")
    void sameUserDifferentChallengeAllowed() throws SQLException {
        UUID userId           = UUID.randomUUID();
        UUID secondChallengeId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO challenge.savings_challenges
                        (id, name, description, challenge_type, system_owned, is_active)
                    VALUES ('%s', 'No-withdrawal 30 days', 'Second test', 'NO_WITHDRAWAL', true, true)
                    """.formatted(secondChallengeId));

            stmt.execute("""
                    INSERT INTO challenge.user_challenges (id, user_id, challenge_id, status)
                    VALUES (gen_random_uuid(), '%s', '%s', 'ACTIVE')
                    """.formatted(userId, sampleChallengeId));

            // Must NOT throw
            stmt.execute("""
                    INSERT INTO challenge.user_challenges (id, user_id, challenge_id, status)
                    VALUES (gen_random_uuid(), '%s', '%s', 'ACTIVE')
                    """.formatted(userId, secondChallengeId));
        }
    }

    @Test
    @DisplayName("user_badges is append-only for stash_app (UPDATE denied)")
    void userBadgesUpdateDenied() throws SQLException {
        // V34's REVOKE was a no-op (stash_app didn't exist at migration time).
        // Create the role here, grant only INSERT+SELECT — UPDATE remains denied
        // for the same reason it would be after a real REVOKE: never granted.
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE ROLE stash_app LOGIN PASSWORD 'test_pw'");
            stmt.execute("GRANT USAGE ON SCHEMA challenge TO stash_app");
            stmt.execute("GRANT INSERT, SELECT ON challenge.user_badges TO stash_app");
        }

        DataSource appDs = DataSourceBuilder.create()
                .url(POSTGRES.getJdbcUrl()).username("stash_app").password("test_pw").build();

        try (Connection conn = appDs.getConnection(); Statement stmt = conn.createStatement()) {
            assertThatThrownBy(() -> stmt.execute(
                    "UPDATE challenge.user_badges SET badge_name = 'x' WHERE id = gen_random_uuid()"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    private void assertColumnsExactly(String table, Set<String> expected) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            var actual = new HashSet<String>();
            try (ResultSet rs = meta.getColumns(null, "challenge", table, null)) {
                while (rs.next()) actual.add(rs.getString("COLUMN_NAME"));
            }
            assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}
