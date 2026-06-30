package com.stash.susu.schema;

import org.junit.jupiter.api.BeforeEach;
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
 * Verifies the susu.susu_contributions migration:
 * - applies cleanly (depends on V11/V12/V13 having run first)
 * - enforces UNIQUE (susu_round_id, member_user_id)
 * - enforces the status CHECK enum (PENDING/PAID/LATE/MISSED/WAIVED)
 * - enforces amount CHECKs
 * - enforces temporal and consistency CHECKs
 * - CASCADE DELETE from susu_rounds and susu_groups works
 *
 * Prerequisite: v0.4-001 (V11), v0.4-002 (V12), and v0.4-003 (V13) must be
 * merged before this migration can apply and before these tests can run.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SusuContributionsSchemaTest {

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

    private UUID groupId;
    private UUID roundId;
    private UUID memberUserId;

    @BeforeEach
    void setUp() {
        groupId      = UUID.randomUUID();
        roundId      = UUID.randomUUID();
        memberUserId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, status, join_code, created_at)
                VALUES (?, gen_random_uuid(), 'Contrib Test Group', 20000,
                        'MONTHLY', 6, 'PENDING', ?, now())
                """, groupId, uniqueCode());

        jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id,
                     status, expected_pot_amount)
                VALUES (?, ?, 1, gen_random_uuid(), 'COLLECTING', 120000)
                """, roundId, groupId);
    }

    // ── Happy paths ────────────────────────────────────────────────────────

    @Test
    @DisplayName("inserts a valid PENDING contribution")
    void valid_pending_contribution_inserts() {
        assertThatCode(() ->
                insertContribution(roundId, groupId, memberUserId,
                        20_000L, "PENDING", false, 0L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inserts a PAID contribution with paid_at and transaction fields")
    void valid_paid_contribution_inserts() {
        UUID txnId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO susu.susu_contributions
                    (id, susu_round_id, susu_group_id, member_user_id,
                     expected_amount, collected_amount, status,
                     is_late, penalty_amount, transaction_id,
                     transaction_reference, paid_at, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?,
                        20000, 20000, 'PAID',
                        false, 0, ?,
                        'STSH-202606-CON001', now(), now())
                """, roundId, groupId, memberUserId, txnId);
    }

    @Test
    @DisplayName("inserts a LATE contribution with is_late=true and penalty")
    void late_contribution_inserts() {
        assertThatCode(() ->
                insertContribution(roundId, groupId, memberUserId,
                        20_000L, "LATE", true, 500L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inserts a MISSED contribution")
    void missed_contribution_inserts() {
        assertThatCode(() ->
                insertContribution(roundId, groupId, memberUserId,
                        20_000L, "MISSED", true, 500L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inserts a WAIVED contribution with paid_at set")
    void waived_contribution_inserts() {
        jdbc.update("""
                INSERT INTO susu.susu_contributions
                    (id, susu_round_id, susu_group_id, member_user_id,
                     expected_amount, status, is_late, penalty_amount,
                     paid_at, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?,
                        20000, 'WAIVED', false, 0,
                        now(), now())
                """, roundId, groupId, memberUserId);
    }

    @Test
    @DisplayName("PAID with is_late=true allowed (member paid late but did pay)")
    void paid_late_combination_allowed() {
        assertThatCode(() ->
                insertContribution(roundId, groupId, memberUserId,
                        20_000L, "PAID", true, 500L))
                .doesNotThrowAnyException();
    }

    // ── UNIQUE (susu_round_id, member_user_id) ─────────────────────────────

    @Test
    @DisplayName("duplicate (susu_round_id, member_user_id) violates unique constraint")
    void duplicate_round_member_fails() {
        insertContribution(roundId, groupId, memberUserId,
                20_000L, "PENDING", false, 0L);

        assertThatThrownBy(() ->
                insertContribution(roundId, groupId, memberUserId,
                        20_000L, "PENDING", false, 0L))
                .hasMessageContaining("susu_contributions_round_member_uk");
    }

    @Test
    @DisplayName("same member in different rounds is allowed")
    void same_member_different_rounds_allowed() {
        UUID round2 = createAdditionalRound(2);
        insertContribution(roundId, groupId, memberUserId,
                20_000L, "PENDING", false, 0L);
        assertThatCode(() ->
                insertContribution(round2, groupId, memberUserId,
                        20_000L, "PENDING", false, 0L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("different members in same round is allowed")
    void different_members_same_round_allowed() {
        UUID member2 = UUID.randomUUID();
        insertContribution(roundId, groupId, memberUserId,
                20_000L, "PENDING", false, 0L);
        assertThatCode(() ->
                insertContribution(roundId, groupId, member2,
                        20_000L, "PENDING", false, 0L))
                .doesNotThrowAnyException();
    }

    // ── Status CHECK ───────────────────────────────────────────────────────

    @Test
    @DisplayName("all five valid statuses accepted")
    void all_valid_statuses_accepted() {
        String[] statuses = { "PENDING", "PAID", "LATE", "MISSED", "WAIVED" };
        UUID[] members    = { UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                              UUID.randomUUID(), UUID.randomUUID() };
        for (int i = 0; i < statuses.length; i++) {
            final int    idx = i;
            final String st  = statuses[i];
            assertThatCode(() ->
                    insertContribution(roundId, groupId, members[idx],
                            20_000L, st, false, 0L))
                    .as("status=%s should be valid", st)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("invalid status FAILED violates CHECK constraint")
    void invalid_status_fails() {
        assertThatThrownBy(() ->
                insertContribution(roundId, groupId, memberUserId,
                        20_000L, "FAILED", false, 0L))
                .hasMessageContaining("susu_contributions_status_check");
    }

    @Test
    @DisplayName("invalid status ACTIVE violates CHECK constraint")
    void invalid_status_active_fails() {
        assertThatThrownBy(() ->
                insertContribution(roundId, groupId, memberUserId,
                        20_000L, "ACTIVE", false, 0L))
                .hasMessageContaining("susu_contributions_status_check");
    }

    // ── Amount CHECKs ──────────────────────────────────────────────────────

    @Test
    @DisplayName("expected_amount = 0 violates CHECK (must be > 0)")
    void zero_expected_amount_fails() {
        assertThatThrownBy(() ->
                insertContribution(roundId, groupId, memberUserId,
                        0L, "PENDING", false, 0L))
                .hasMessageContaining("susu_contributions_expected_amount_positive");
    }

    @Test
    @DisplayName("negative penalty_amount violates CHECK")
    void negative_penalty_fails() {
        assertThatThrownBy(() ->
                insertContribution(roundId, groupId, memberUserId,
                        20_000L, "PENDING", false, -1L))
                .hasMessageContaining("susu_contributions_penalty_amount_non_negative");
    }

    @Test
    @DisplayName("expected_amount = 1 pesewa is valid (boundary)")
    void minimum_expected_amount_valid() {
        assertThatCode(() ->
                insertContribution(roundId, groupId, memberUserId,
                        1L, "PENDING", false, 0L))
                .doesNotThrowAnyException();
    }

    // ── Consistency CHECKs ─────────────────────────────────────────────────

    @Test
    @DisplayName("paid_at on PENDING contribution violates CHECK")
    void paid_at_on_pending_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO susu.susu_contributions
                    (id, susu_round_id, susu_group_id, member_user_id,
                     expected_amount, status, is_late, penalty_amount,
                     paid_at, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?,
                        20000, 'PENDING', false, 0, now(), now())
                """, roundId, groupId, memberUserId))
                .hasMessageContaining("susu_contributions_paid_at_requires_paid");
    }

    @Test
    @DisplayName("paid_at on LATE contribution violates CHECK")
    void paid_at_on_late_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO susu.susu_contributions
                    (id, susu_round_id, susu_group_id, member_user_id,
                     expected_amount, status, is_late, penalty_amount,
                     paid_at, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?,
                        20000, 'LATE', true, 500, now(), now())
                """, roundId, groupId, memberUserId))
                .hasMessageContaining("susu_contributions_paid_at_requires_paid");
    }

    @Test
    @DisplayName("is_late=true on WAIVED contribution violates CHECK (waived != late)")
    void is_late_on_waived_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO susu.susu_contributions
                    (id, susu_round_id, susu_group_id, member_user_id,
                     expected_amount, status, is_late, penalty_amount,
                     paid_at, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?,
                        20000, 'WAIVED', true, 0, now(), now())
                """, roundId, groupId, memberUserId))
                .hasMessageContaining("susu_contributions_is_late_status_consistency");
    }

    @Test
    @DisplayName("transaction_id on PENDING contribution violates CHECK")
    void txn_id_on_pending_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO susu.susu_contributions
                    (id, susu_round_id, susu_group_id, member_user_id,
                     expected_amount, status, is_late, penalty_amount,
                     transaction_id, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?,
                        20000, 'PENDING', false, 0, gen_random_uuid(), now())
                """, roundId, groupId, memberUserId))
                .hasMessageContaining("susu_contributions_txn_requires_paid");
    }

    // ── CASCADE DELETE ─────────────────────────────────────────────────────

    @Test
    @DisplayName("deleting a susu_round cascades to delete its contributions")
    void cascade_delete_from_round() {
        insertContribution(roundId, groupId, memberUserId,
                20_000L, "PENDING", false, 0L);
        insertContribution(roundId, groupId, UUID.randomUUID(),
                20_000L, "PENDING", false, 0L);

        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM susu.susu_contributions WHERE susu_round_id = ?",
                Integer.class, roundId);
        assertThat(before).isEqualTo(2);

        jdbc.update("DELETE FROM susu.susu_rounds WHERE id = ?", roundId);

        Integer after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM susu.susu_contributions WHERE susu_round_id = ?",
                Integer.class, roundId);
        assertThat(after).isEqualTo(0);
    }

    @Test
    @DisplayName("deleting a susu_group cascades to delete contributions via rounds")
    void cascade_delete_from_group() {
        insertContribution(roundId, groupId, memberUserId,
                20_000L, "PENDING", false, 0L);

        jdbc.update("DELETE FROM susu.susu_groups WHERE id = ?", groupId);

        Integer after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM susu.susu_contributions WHERE susu_group_id = ?",
                Integer.class, groupId);
        assertThat(after).isEqualTo(0);
    }

    // ── Schema structure ───────────────────────────────────────────────────

    @Test
    @DisplayName("susu_contributions table has correct column count")
    void table_has_correct_columns() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'susu' AND table_name = 'susu_contributions'
                """, Integer.class);
        // 14 columns: id, susu_round_id, susu_group_id, member_user_id,
        //   expected_amount, collected_amount, status, collection_attempt_count,
        //   penalty_amount, is_late, transaction_id, transaction_reference,
        //   paid_at, created_at
        assertThat(count).isEqualTo(14);
    }

    @Test
    @DisplayName("round_status composite index exists")
    void round_status_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_contributions'
                  AND indexname  = 'susu_contributions_round_status_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("member_group composite index exists")
    void member_group_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_contributions'
                  AND indexname  = 'susu_contributions_member_group_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("group_status composite index exists")
    void group_status_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_contributions'
                  AND indexname  = 'susu_contributions_group_status_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void insertContribution(UUID roundId, UUID groupId, UUID memberUserId,
                                     long expectedAmount, String status,
                                     boolean isLate, long penaltyAmount) {
        jdbc.update("""
                INSERT INTO susu.susu_contributions
                    (id, susu_round_id, susu_group_id, member_user_id,
                     expected_amount, status, is_late, penalty_amount, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, now())
                """,
                roundId, groupId, memberUserId,
                expectedAmount, status, isLate, penaltyAmount);
    }

    private UUID createAdditionalRound(int roundNumber) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id,
                     status, expected_pot_amount)
                VALUES (?, ?, ?, gen_random_uuid(), 'PENDING', 120000)
                """, id, groupId, roundNumber);
        return id;
    }

    private String uniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
