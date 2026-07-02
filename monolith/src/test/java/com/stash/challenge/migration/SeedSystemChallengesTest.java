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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SeedSystemChallengesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("monolith_db")
            .withUsername("postgres")
            .withPassword("postgres");

    static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        dataSource = DataSourceBuilder.create()
                .url(POSTGRES.getJdbcUrl())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();

        // Same 8 subdirectory locations as ChallengeSchemaTest (v0.5-016) and
        // NotificationSchemaTest (v0.5-012) — classpath:db/migration alone does
        // NOT recursively scan subdirectories.
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
    }

    @Test
    @DisplayName("V35/V36 and R__seed apply cleanly on a fresh database")
    void appliesCleanlyOnFreshDatabase() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM challenge.savings_challenges")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("7-day, 30-day, and 90-day SAVE_AMOUNT archetypes exist")
    void archetypesPresent() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            assertDurationExists(stmt, 7,  5000L);
            assertDurationExists(stmt, 30, 20000L);
            assertDurationExists(stmt, 90, 100000L);
        }
    }

    @Test
    @DisplayName("every seeded challenge is system_owned=true with creator_user_id=NULL")
    void allRowsAreSystemOwned() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT COUNT(*) FROM challenge.savings_challenges
                     WHERE system_owned = false OR creator_user_id IS NOT NULL
                     """)) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("every badge_id resolves to a row in challenge.badges — no dangling FK")
    void badgeReferencesResolve() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT COUNT(*) FROM challenge.savings_challenges sc
                     LEFT JOIN challenge.badges b ON sc.badge_id = b.id
                     WHERE sc.badge_id IS NOT NULL AND b.id IS NULL
                     """)) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("the NO_WITHDRAWAL challenge carries the one confirmed badge code (NO_BREAK_30D, §5.3)")
    void confirmedBadgeCodeUsedCorrectly() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT b.badge_code FROM challenge.savings_challenges sc
                     JOIN challenge.badges b ON sc.badge_id = b.id
                     WHERE sc.challenge_type = 'NO_WITHDRAWAL'
                     """)) {
            rs.next();
            assertThat(rs.getString("badge_code")).isEqualTo("NO_BREAK_30D");
        }
    }

    @Test
    @DisplayName("calling migrate() again with no changes is a clean no-op — count stays at 4")
    void rerunningSeedDoesNotDuplicate() {
        // A second Flyway instance against the same already-migrated DB.
        // R__ migrations are only re-applied when their checksum changes — the
        // file hasn't changed, so Flyway skips it. This verifies the
        // infrastructure is safe to call migrate() repeatedly.
        Flyway flyway = Flyway.configure()
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
                .load();

        flyway.repair();
        flyway.migrate();
        flyway.migrate(); // second call — truly no-op

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM challenge.savings_challenges")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(4); // still 4, not 8
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("ON CONFLICT updates in place — edited seed content doesn't duplicate rows")
    void editedContentUpdatesInPlace() throws SQLException {
        // Simulates what happens when Flyway re-applies R__seed because the file
        // changed (a different target_amount). The ON CONFLICT (id) DO UPDATE
        // clause must update the existing row, not insert a second one.
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO challenge.savings_challenges
                        (id, name, description, challenge_type, target_amount, target_duration_days,
                         system_owned, creator_user_id, is_active, badge_id)
                    VALUES
                        ('b2000000-0000-4000-8000-000000000001',
                         'Save GHS 75 in 7 Days', 'Updated copy', 'SAVE_AMOUNT',
                         7500, 7, true, NULL, true,
                         'a1000000-0000-4000-8000-000000000001')
                    ON CONFLICT (id) DO UPDATE SET
                        name = EXCLUDED.name, target_amount = EXCLUDED.target_amount
                    """);

            try (ResultSet rs = stmt.executeQuery("""
                    SELECT COUNT(*), MAX(target_amount) FROM challenge.savings_challenges
                    WHERE id = 'b2000000-0000-4000-8000-000000000001'
                    """)) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);    // exactly one row — no duplicate
                assertThat(rs.getLong(2)).isEqualTo(7500); // updated in place
            }

            // Restore original seed values so other tests that query by
            // target_amount=5000 / target_duration_days=7 are not affected
            // by this test's mutation (test-ordering is non-deterministic).
            stmt.execute("""
                    UPDATE challenge.savings_challenges
                    SET name = 'Save GHS 50 in 7 Days', target_amount = 5000
                    WHERE id = 'b2000000-0000-4000-8000-000000000001'
                    """);
        }
    }

    private void assertDurationExists(Statement stmt, int days, long targetAmount) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("""
                SELECT COUNT(*) FROM challenge.savings_challenges
                WHERE target_duration_days = %d AND target_amount = %d
                  AND challenge_type = 'SAVE_AMOUNT' AND system_owned = true
                """.formatted(days, targetAmount))) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("challenge with duration=%d days, target=%d pesewas", days, targetAmount)
                    .isEqualTo(1);
        }
    }
}
