package com.stash.susu.schema;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies that the susu.susu_groups migration:
 * - applies cleanly on a fresh database
 * - enforces the join_code UNIQUE constraint
 * - enforces the target_member_count CHECK (4-20)
 * - enforces the status CHECK enum
 * - enforces the frequency CHECK enum
 * - enforces the contribution_amount > 0 CHECK
 *
 * Uses Testcontainers with a real Postgres instance. Flyway runs automatically
 * on context start via DataJpaTest + the configured Flyway locations.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SusuGroupsSchemaTest {

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

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("inserts a valid PENDING group")
    void valid_group_inserts() {
        String ref = insertGroup("STASH CIRCLE", 5_000L, "MONTHLY", 6,
                "PENDING", "JOIN1234");
        assertThat(ref).isNotNull();
    }

    @Test
    @DisplayName("inserts a valid ACTIVE group with start_date set")
    void valid_active_group_inserts() {
        String code = uniqueCode();
        jdbc.update("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, start_date,
                     status, current_round_number, join_code, created_at)
                VALUES
                    (gen_random_uuid(), gen_random_uuid(), 'Active Group',
                     10000, 'WEEKLY', 4, CURRENT_DATE,
                     'ACTIVE', 1, ?, now())
                """, code);
    }

    // ── join_code uniqueness ───────────────────────────────────────────────

    @Test
    @DisplayName("duplicate join_code violates UNIQUE constraint")
    void duplicate_join_code_fails() {
        insertGroup("Group A", 5_000L, "MONTHLY", 6, "PENDING", "DUPCODE1");
        assertThatThrownBy(() ->
                insertGroup("Group B", 5_000L, "MONTHLY", 6, "PENDING", "DUPCODE1"))
                .hasMessageContaining("susu_groups_join_code_uk");
    }

    // ── target_member_count CHECK ──────────────────────────────────────────

    @Test
    @DisplayName("target_member_count = 3 violates CHECK (minimum is 4)")
    void target_member_count_below_minimum_fails() {
        assertThatThrownBy(() ->
                insertGroup("Too Small", 5_000L, "MONTHLY", 3, "PENDING", uniqueCode()))
                .hasMessageContaining("susu_groups_target_member_count_check");
    }

    @Test
    @DisplayName("target_member_count = 21 violates CHECK (maximum is 20)")
    void target_member_count_above_maximum_fails() {
        assertThatThrownBy(() ->
                insertGroup("Too Large", 5_000L, "MONTHLY", 21, "PENDING", uniqueCode()))
                .hasMessageContaining("susu_groups_target_member_count_check");
    }

    @Test
    @DisplayName("target_member_count = 4 is valid (boundary)")
    void target_member_count_minimum_boundary_valid() {
        assertThatCode(() ->
                insertGroup("Min Size", 5_000L, "MONTHLY", 4, "PENDING", uniqueCode()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("target_member_count = 20 is valid (boundary)")
    void target_member_count_maximum_boundary_valid() {
        assertThatCode(() ->
                insertGroup("Max Size", 5_000L, "MONTHLY", 20, "PENDING", uniqueCode()))
                .doesNotThrowAnyException();
    }

    // ── status CHECK ───────────────────────────────────────────────────────

    @Test
    @DisplayName("status = PENDING is valid")
    void status_pending_valid() {
        assertThatCode(() ->
                insertGroup("Pending Group", 5_000L, "MONTHLY", 6, "PENDING", uniqueCode()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("status = ACTIVE is valid")
    void status_active_valid() {
        String code = uniqueCode();
        assertThatCode(() -> jdbc.update("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, start_date,
                     status, current_round_number, join_code, created_at)
                VALUES
                    (gen_random_uuid(), gen_random_uuid(), 'Active',
                     5000, 'WEEKLY', 6, CURRENT_DATE,
                     'ACTIVE', 1, ?, now())
                """, code))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("status = COMPLETED is valid")
    void status_completed_valid() {
        assertThatCode(() ->
                insertGroupWithStartDate("Completed", 5_000L, "MONTHLY", 6,
                        "COMPLETED", uniqueCode()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("status = CANCELLED is valid")
    void status_cancelled_valid() {
        assertThatCode(() ->
                insertGroupWithStartDate("Cancelled", 5_000L, "MONTHLY", 6,
                        "CANCELLED", uniqueCode()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("invalid status value violates CHECK constraint")
    void invalid_status_fails() {
        assertThatThrownBy(() ->
                insertGroup("Bad Status", 5_000L, "MONTHLY", 6, "DORMANT", uniqueCode()))
                .hasMessageContaining("susu_groups_status_check");
    }

    @Test
    @DisplayName("status = FROZEN is valid (V44, v0.5-029 — no prior test coverage for this value)")
    void frozen_status_valid() {
        assertThatCode(() ->
                insertGroup("Frozen Group", 5_000L, "MONTHLY", 6, "FROZEN", uniqueCode()))
                .doesNotThrowAnyException();
    }

    // ── flagged_for_review / flagged_at (V46, v0.5-034) ─────────────────────

    @Test
    @DisplayName("new group defaults to flagged_for_review = false, flagged_at = NULL")
    void flagged_for_review_defaults_false() {
        String id = insertGroup("Unflagged Group", 5_000L, "MONTHLY", 6, "PENDING", uniqueCode());

        Boolean flagged = jdbc.queryForObject(
                "SELECT flagged_for_review FROM susu.susu_groups WHERE id = ?::UUID", Boolean.class, id);
        Object flaggedAt = jdbc.queryForObject(
                "SELECT flagged_at FROM susu.susu_groups WHERE id = ?::UUID", Object.class, id);

        assertThat(flagged).isFalse();
        assertThat(flaggedAt).isNull();
    }

    @Test
    @DisplayName("a group can be flagged with flagged_for_review = true and a flagged_at timestamp")
    void group_can_be_flagged() {
        String id = insertGroup("To Be Flagged", 5_000L, "MONTHLY", 6, "ACTIVE", uniqueCode());

        jdbc.update("UPDATE susu.susu_groups SET flagged_for_review = true, flagged_at = now() WHERE id = ?::UUID", id);

        Boolean flagged = jdbc.queryForObject(
                "SELECT flagged_for_review FROM susu.susu_groups WHERE id = ?::UUID", Boolean.class, id);
        assertThat(flagged).isTrue();
    }

    // ── frequency CHECK ────────────────────────────────────────────────────

    @Test
    @DisplayName("all three valid frequencies accepted")
    void valid_frequencies_accepted() {
        for (String freq : new String[]{"WEEKLY", "BIWEEKLY", "MONTHLY"}) {
            assertThatCode(() ->
                    insertGroup("Group " + freq, 5_000L, freq, 6, "PENDING", uniqueCode()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("invalid frequency value violates CHECK constraint")
    void invalid_frequency_fails() {
        assertThatThrownBy(() ->
                insertGroup("Bad Freq", 5_000L, "FORTNIGHTLY", 6, "PENDING", uniqueCode()))
                .hasMessageContaining("susu_groups_frequency_check");
    }

    // ── contribution_amount CHECK ──────────────────────────────────────────

    @Test
    @DisplayName("contribution_amount = 0 violates CHECK (must be > 0)")
    void zero_contribution_fails() {
        assertThatThrownBy(() ->
                insertGroup("Zero Contrib", 0L, "MONTHLY", 6, "PENDING", uniqueCode()))
                .hasMessageContaining("susu_groups_contribution_amount_positive");
    }

    @Test
    @DisplayName("contribution_amount = 1 pesewa is valid (minimum)")
    void minimum_contribution_valid() {
        assertThatCode(() ->
                insertGroup("Min Contrib", 1L, "MONTHLY", 6, "PENDING", uniqueCode()))
                .doesNotThrowAnyException();
    }

    // ── Schema structure ───────────────────────────────────────────────────

    @Test
    @DisplayName("susu schema exists")
    void susu_schema_exists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'susu'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("susu_groups table exists with correct column count")
    void susu_groups_table_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'susu' AND table_name = 'susu_groups'
                """, Integer.class);
        assertThat(count).isEqualTo(12);  // 12 columns: 11 original + ledger_account_id (v0.4-008)
    }

    @Test
    @DisplayName("join_code UNIQUE index exists")
    void join_code_unique_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_groups'
                  AND indexname  = 'susu_groups_join_code_uk'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("status index exists")
    void status_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_groups'
                  AND indexname  = 'susu_groups_status_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("organiser index exists")
    void organiser_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_groups'
                  AND indexname  = 'susu_groups_organiser_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── start_date CHECK ───────────────────────────────────────────────────

    @Test
    @DisplayName("start_date set on PENDING group violates CHECK")
    void start_date_on_pending_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, start_date,
                     status, join_code, created_at)
                VALUES
                    (gen_random_uuid(), gen_random_uuid(), 'Bad Group',
                     5000, 'MONTHLY', 6, CURRENT_DATE,
                     'PENDING', ?, now())
                """, uniqueCode()))
                .hasMessageContaining("susu_groups_start_date_requires_active");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String insertGroup(String name, long amount, String frequency,
                                int targetCount, String status, String joinCode) {
        return jdbc.queryForObject("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, status, join_code, created_at)
                VALUES
                    (gen_random_uuid(), gen_random_uuid(), ?, ?, ?, ?, ?, ?, now())
                RETURNING id::TEXT
                """,
                String.class,
                name, amount, frequency, targetCount, status, joinCode);
    }

    private void insertGroupWithStartDate(String name, long amount, String frequency,
                                           int targetCount, String status, String joinCode) {
        jdbc.update("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, start_date,
                     status, join_code, created_at)
                VALUES
                    (gen_random_uuid(), gen_random_uuid(), ?, ?, ?, ?,
                     CURRENT_DATE, ?, ?, now())
                """,
                name, amount, frequency, targetCount, status, joinCode);
    }

    /** Generates a unique 8-char code safe for multiple test runs in the same DB. */
    private String uniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
