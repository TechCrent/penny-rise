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
 * Verifies the susu.susu_rounds migration:
 * - applies cleanly (depends on V11/V12 having run first)
 * - enforces partial UNIQUE (susu_group_id, round_number) WHERE status != 'SKIPPED'
 * - enforces the status CHECK enum (including DISBURSING per issue #99)
 * - enforces temporal consistency CHECKs
 * - enforces positive/non-negative amount CHECKs
 * - CASCADE DELETE from susu_groups works
 *
 * Prerequisite: v0.4-001 (V11) and v0.4-002 (V12) must be merged before this
 * migration can apply and before these tests can run in isolation.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SusuRoundsSchemaTest {

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
    private UUID recipientUserId;

    @BeforeEach
    void setUp() {
        groupId         = UUID.randomUUID();
        recipientUserId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, status, join_code, created_at)
                VALUES (?, gen_random_uuid(), 'Round Test Group', 20000,
                        'MONTHLY', 6, 'PENDING', ?, now())
                """,
                groupId, uniqueCode());
    }

    // ── Happy paths ────────────────────────────────────────────────────────

    @Test
    @DisplayName("inserts a valid PENDING round with minimal fields")
    void valid_pending_round_inserts() {
        assertThatCode(() ->
                insertRound(groupId, 1, recipientUserId, "PENDING", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inserts a COLLECTING round with scheduled_collection_at")
    void collecting_round_with_schedule_inserts() {
        jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id,
                     status, scheduled_collection_at, expected_pot_amount)
                VALUES (gen_random_uuid(), ?, 1, ?, 'COLLECTING',
                        now() + INTERVAL '7 days', 120000)
                """, groupId, recipientUserId);
    }

    @Test
    @DisplayName("inserts a DISBURSING round (issue #99 status enum)")
    void disbursing_round_inserts() {
        jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id,
                     status, expected_pot_amount, actual_pot_amount, disbursed_at)
                VALUES (gen_random_uuid(), ?, 1, ?,
                        'DISBURSING', 120000, 120000, now())
                """, groupId, recipientUserId);
    }

    @Test
    @DisplayName("inserts a DISBURSED round with disbursement fields set")
    void disbursed_round_inserts() {
        UUID txnId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id, status,
                     expected_pot_amount, actual_pot_amount,
                     disbursed_at, disbursement_transaction_id)
                VALUES (gen_random_uuid(), ?, 1, ?,
                        'DISBURSED', 120000, 118000, now(), ?)
                """, groupId, recipientUserId, txnId);
    }

    @Test
    @DisplayName("inserts a SKIPPED round (removed-member edge case)")
    void skipped_round_inserts() {
        assertThatCode(() ->
                insertRound(groupId, 1, recipientUserId, "SKIPPED", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inserts a round with null recipient_user_id (nullable per issue spec)")
    void null_recipient_inserts() {
        assertThatCode(() ->
                insertRound(groupId, 1, null, "PENDING", null, null))
                .doesNotThrowAnyException();
    }

    // ── Partial unique (susu_group_id, round_number) WHERE status != 'SKIPPED' ──

    @Test
    @DisplayName("duplicate round_number in same group violates the partial unique index")
    void duplicate_round_number_same_group_fails() {
        insertRound(groupId, 1, recipientUserId, "PENDING", null, null);

        assertThatThrownBy(() ->
                insertRound(groupId, 1, UUID.randomUUID(), "PENDING", null, null))
                .hasMessageContaining("susu_rounds_group_round_uk");
    }

    @Test
    @DisplayName("a SKIPPED round's number can be reused by another round in the same group")
    void skipped_round_number_can_be_reused() {
        insertRound(groupId, 1, recipientUserId, "SKIPPED", null, null);

        assertThatCode(() ->
                insertRound(groupId, 1, UUID.randomUUID(), "PENDING", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("same round_number in different groups is allowed")
    void same_round_number_different_groups_allowed() {
        UUID group2 = createAdditionalGroup();
        insertRound(groupId,  1, recipientUserId, "PENDING", null, null);
        assertThatCode(() ->
                insertRound(group2, 1, UUID.randomUUID(), "PENDING", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sequential round numbers in same group all succeed")
    void sequential_rounds_in_same_group_succeed() {
        for (int i = 1; i <= 6; i++) {
            final int num = i;
            assertThatCode(() ->
                    insertRound(groupId, num, UUID.randomUUID(), "PENDING", null, null))
                    .doesNotThrowAnyException();
        }
    }

    // ── Status CHECK ───────────────────────────────────────────────────────

    @Test
    @DisplayName("all six valid statuses accepted")
    void all_valid_statuses_accepted() {
        String[] statuses = { "PENDING", "COLLECTING", "DISBURSING",
                              "DISBURSED", "COMPLETED", "SKIPPED" };
        for (int i = 0; i < statuses.length; i++) {
            final int num    = i + 1;
            final String st  = statuses[i];
            assertThatCode(() ->
                    insertRound(groupId, num, recipientUserId, st, null, null))
                    .as("status=%s should be valid", st)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("invalid status PAID violates CHECK constraint")
    void invalid_status_paid_fails() {
        assertThatThrownBy(() ->
                insertRound(groupId, 1, recipientUserId, "PAID", null, null))
                .hasMessageContaining("susu_rounds_status_check");
    }

    @Test
    @DisplayName("invalid status ACTIVE violates CHECK constraint")
    void invalid_status_active_fails() {
        assertThatThrownBy(() ->
                insertRound(groupId, 1, recipientUserId, "ACTIVE", null, null))
                .hasMessageContaining("susu_rounds_status_check");
    }

    // ── round_number CHECK ─────────────────────────────────────────────────

    @Test
    @DisplayName("round_number = 0 violates CHECK (minimum is 1)")
    void zero_round_number_fails() {
        assertThatThrownBy(() ->
                insertRound(groupId, 0, recipientUserId, "PENDING", null, null))
                .hasMessageContaining("susu_rounds_round_number_positive");
    }

    @Test
    @DisplayName("round_number = 1 is valid (boundary)")
    void minimum_round_number_valid() {
        assertThatCode(() ->
                insertRound(groupId, 1, recipientUserId, "PENDING", null, null))
                .doesNotThrowAnyException();
    }

    // ── Amount CHECKs ──────────────────────────────────────────────────────

    @Test
    @DisplayName("expected_pot_amount = 0 violates CHECK (must be > 0)")
    void zero_expected_pot_fails() {
        assertThatThrownBy(() ->
                insertRound(groupId, 1, recipientUserId, "PENDING", 0L, null))
                .hasMessageContaining("susu_rounds_expected_pot_positive");
    }

    @Test
    @DisplayName("actual_pot_amount = 0 is allowed (all contributions missed)")
    void zero_actual_pot_allowed() {
        assertThatCode(() ->
                insertRound(groupId, 1, recipientUserId, "COLLECTING", 120_000L, 0L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("expected_pot_amount = 1 pesewa is valid (boundary)")
    void minimum_expected_pot_valid() {
        assertThatCode(() ->
                insertRound(groupId, 1, recipientUserId, "PENDING", 1L, null))
                .doesNotThrowAnyException();
    }

    // ── Temporal consistency CHECKs ────────────────────────────────────────

    @Test
    @DisplayName("disbursed_at on PENDING round violates CHECK")
    void disbursed_at_on_pending_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id,
                     status, disbursed_at)
                VALUES (gen_random_uuid(), ?, 1, ?, 'PENDING', now())
                """, groupId, recipientUserId))
                .hasMessageContaining("susu_rounds_disbursed_at_requires_terminal");
    }

    @Test
    @DisplayName("disbursed_at on COLLECTING round violates CHECK")
    void disbursed_at_on_collecting_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id,
                     status, disbursed_at)
                VALUES (gen_random_uuid(), ?, 1, ?, 'COLLECTING', now())
                """, groupId, recipientUserId))
                .hasMessageContaining("susu_rounds_disbursed_at_requires_terminal");
    }

    @Test
    @DisplayName("disbursement_transaction_id on PENDING round violates CHECK")
    void txn_id_on_pending_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id,
                     status, disbursement_transaction_id)
                VALUES (gen_random_uuid(), ?, 1, ?, 'PENDING', gen_random_uuid())
                """, groupId, recipientUserId))
                .hasMessageContaining("susu_rounds_disbursement_txn_requires_terminal");
    }

    @Test
    @DisplayName("disbursed_at on DISBURSING round is allowed")
    void disbursed_at_on_disbursing_allowed() {
        assertThatCode(() -> jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id,
                     status, expected_pot_amount, actual_pot_amount, disbursed_at)
                VALUES (gen_random_uuid(), ?, 1, ?,
                        'DISBURSING', 120000, 120000, now())
                """, groupId, recipientUserId))
                .doesNotThrowAnyException();
    }

    // ── CASCADE DELETE ─────────────────────────────────────────────────────

    @Test
    @DisplayName("deleting a susu_group cascades to delete its rounds")
    void cascade_delete_from_group() {
        insertRound(groupId, 1, recipientUserId, "PENDING", 120_000L, null);
        insertRound(groupId, 2, UUID.randomUUID(), "PENDING", 120_000L, null);

        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM susu.susu_rounds WHERE susu_group_id = ?",
                Integer.class, groupId);
        assertThat(before).isEqualTo(2);

        jdbc.update("DELETE FROM susu.susu_groups WHERE id = ?", groupId);

        Integer after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM susu.susu_rounds WHERE susu_group_id = ?",
                Integer.class, groupId);
        assertThat(after).isEqualTo(0);
    }

    // ── Schema structure ───────────────────────────────────────────────────

    @Test
    @DisplayName("susu_rounds table has correct column count")
    void table_has_correct_columns() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'susu' AND table_name = 'susu_rounds'
                """, Integer.class);
        assertThat(count).isEqualTo(10);  // matches Schema doc §3.5
    }

    @Test
    @DisplayName("partial unique index on (susu_group_id, round_number) exists")
    void group_round_unique_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_rounds'
                  AND indexname  = 'susu_rounds_group_round_uk'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("status index exists")
    void status_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_rounds'
                  AND indexname  = 'susu_rounds_status_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("group + round_number composite index exists")
    void group_round_composite_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_rounds'
                  AND indexname  = 'susu_rounds_group_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void insertRound(UUID groupId, int roundNumber, UUID recipientUserId,
                              String status, Long expectedPot, Long actualPot) {
        jdbc.update("""
                INSERT INTO susu.susu_rounds
                    (id, susu_group_id, round_number, recipient_user_id,
                     status, expected_pot_amount, actual_pot_amount)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?)
                """,
                groupId, roundNumber, recipientUserId, status, expectedPot, actualPot);
    }

    private UUID createAdditionalGroup() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, status, join_code, created_at)
                VALUES (?, gen_random_uuid(), 'Group 2', 20000,
                        'MONTHLY', 6, 'PENDING', ?, now())
                """,
                id, uniqueCode());
        return id;
    }

    private String uniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
