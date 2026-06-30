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
 * Verifies the susu.susu_memberships migration:
 * - applies cleanly (depends on V11__create_susu_groups having run first)
 * - enforces UNIQUE (susu_group_id, user_id)
 * - enforces partial UNIQUE (susu_group_id, rotation_position) WHERE status = 'ACTIVE'
 * - enforces the status CHECK enum
 * - enforces removed_at consistency CHECK
 * - CASCADE DELETE from susu_groups works
 *
 * Prerequisite: v0.4-001 (V11__create_susu_groups) must be merged before this
 * migration can apply and before these tests can run in isolation.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SusuMembershipsSchemaTest {

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

    @BeforeEach
    void createGroup() {
        groupId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, status, join_code, created_at)
                VALUES (?, gen_random_uuid(), 'Test Group', 5000, 'MONTHLY', 6, 'PENDING', ?, now())
                """,
                groupId, uniqueCode());
    }

    // ── Happy paths ────────────────────────────────────────────────────────

    @Test
    @DisplayName("inserts a valid ACTIVE membership with null rotation_position")
    void valid_pending_membership_inserts() {
        assertThatCode(() -> insertMembership(groupId, UUID.randomUUID(), null, "ACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inserts a valid ACTIVE membership with a rotation_position set")
    void valid_active_membership_with_position_inserts() {
        assertThatCode(() -> insertMembership(groupId, UUID.randomUUID(), 1, "ACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inserts a REMOVED membership with removed_at set")
    void removed_membership_with_timestamp_inserts() {
        jdbc.update("""
                INSERT INTO susu.susu_memberships
                    (id, susu_group_id, user_id, rotation_position, status, joined_at, removed_at)
                VALUES (gen_random_uuid(), ?, ?, 2, 'REMOVED', now(), now())
                """, groupId, UUID.randomUUID());
    }

    @Test
    @DisplayName("inserts a COMPLETED membership")
    void completed_membership_inserts() {
        assertThatCode(() -> insertMembership(groupId, UUID.randomUUID(), 3, "COMPLETED"))
                .doesNotThrowAnyException();
    }

    // ── Duplicate (susu_group_id, user_id) ────────────────────────────────

    @Test
    @DisplayName("duplicate (susu_group_id, user_id) violates UNIQUE constraint")
    void duplicate_group_user_pair_fails() {
        UUID userId = UUID.randomUUID();
        insertMembership(groupId, userId, null, "ACTIVE");

        assertThatThrownBy(() -> insertMembership(groupId, userId, null, "ACTIVE"))
                .hasMessageContaining("susu_memberships_group_user_uk");
    }

    @Test
    @DisplayName("same user can join different groups — no constraint violation")
    void same_user_different_groups_allowed() {
        UUID userId = UUID.randomUUID();
        UUID group2 = createAdditionalGroup();

        insertMembership(groupId, userId, 1, "ACTIVE");
        assertThatCode(() -> insertMembership(group2, userId, 1, "ACTIVE"))
                .doesNotThrowAnyException();
    }

    // ── Partial unique index: ACTIVE rotation_position ─────────────────────

    @Test
    @DisplayName("two ACTIVE members with same rotation_position in same group violates partial unique index")
    void duplicate_active_position_in_same_group_fails() {
        insertMembership(groupId, UUID.randomUUID(), 1, "ACTIVE");

        assertThatThrownBy(() -> insertMembership(groupId, UUID.randomUUID(), 1, "ACTIVE"))
                .hasMessageContaining("susu_memberships_active_position_uk");
    }

    @Test
    @DisplayName("ACTIVE and REMOVED members may share the same rotation_position")
    void active_and_removed_same_position_allowed() {
        UUID removed = UUID.randomUUID();
        UUID newMember = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO susu.susu_memberships
                    (id, susu_group_id, user_id, rotation_position, status, joined_at, removed_at)
                VALUES (gen_random_uuid(), ?, ?, 1, 'REMOVED', now(), now())
                """, groupId, removed);

        assertThatCode(() -> insertMembership(groupId, newMember, 1, "ACTIVE"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("multiple ACTIVE members with null rotation_position allowed (pre-activation)")
    void multiple_null_positions_allowed() {
        assertThatCode(() -> {
            insertMembership(groupId, UUID.randomUUID(), null, "ACTIVE");
            insertMembership(groupId, UUID.randomUUID(), null, "ACTIVE");
            insertMembership(groupId, UUID.randomUUID(), null, "ACTIVE");
        }).doesNotThrowAnyException();
    }

    // ── status CHECK ───────────────────────────────────────────────────────

    @Test
    @DisplayName("invalid status value violates CHECK constraint")
    void invalid_status_fails() {
        // 'LEFT' is now valid (v0.4-012); use an entirely unknown value instead
        assertThatThrownBy(() -> insertMembership(groupId, UUID.randomUUID(), null, "INACTIVE"))
                .hasMessageContaining("susu_memberships_status_check");
    }

    // ── rotation_position CHECK ────────────────────────────────────────────

    @Test
    @DisplayName("rotation_position = 0 violates CHECK (minimum is 1)")
    void rotation_position_zero_fails() {
        assertThatThrownBy(() -> insertMembership(groupId, UUID.randomUUID(), 0, "ACTIVE"))
                .hasMessageContaining("susu_memberships_rotation_position_positive");
    }

    // ── removed_at consistency CHECK ───────────────────────────────────────

    @Test
    @DisplayName("removed_at set on ACTIVE membership violates CHECK")
    void removed_at_on_active_member_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO susu.susu_memberships
                    (id, susu_group_id, user_id, status, joined_at, removed_at)
                VALUES (gen_random_uuid(), ?, ?, 'ACTIVE', now(), now())
                """, groupId, UUID.randomUUID()))
                .hasMessageContaining("susu_memberships_removed_at_requires_non_active");
    }

    @Test
    @DisplayName("removed_at null for COMPLETED membership is allowed")
    void removed_at_null_for_completed_allowed() {
        assertThatCode(() -> insertMembership(groupId, UUID.randomUUID(), 4, "COMPLETED"))
                .doesNotThrowAnyException();
    }

    // ── FK and CASCADE ─────────────────────────────────────────────────────

    @Test
    @DisplayName("FK to non-existent susu_group_id fails")
    void foreign_key_to_nonexistent_group_fails() {
        assertThatThrownBy(() ->
                insertMembership(UUID.randomUUID(), UUID.randomUUID(), null, "ACTIVE"))
                .hasMessageContaining("susu_memberships_group_fk");
    }

    @Test
    @DisplayName("deleting a susu_group cascades and removes its memberships")
    void cascade_delete_removes_memberships() {
        insertMembership(groupId, UUID.randomUUID(), null, "ACTIVE");
        insertMembership(groupId, UUID.randomUUID(), null, "ACTIVE");

        jdbc.update("DELETE FROM susu.susu_groups WHERE id = ?", groupId);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM susu.susu_memberships WHERE susu_group_id = ?",
                Integer.class, groupId);
        assertThat(count).isZero();
    }

    // ── Schema structure ───────────────────────────────────────────────────

    @Test
    @DisplayName("susu_memberships table exists with correct column count")
    void susu_memberships_table_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'susu' AND table_name = 'susu_memberships'
                """, Integer.class);
        assertThat(count).isEqualTo(7);
    }

    @Test
    @DisplayName("group_user UNIQUE index exists")
    void group_user_unique_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_memberships'
                  AND indexname  = 'susu_memberships_group_user_uk'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("active_position partial unique index exists")
    void active_position_unique_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_memberships'
                  AND indexname  = 'susu_memberships_active_position_uk'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("user partial index exists")
    void user_idx_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'susu'
                  AND tablename  = 'susu_memberships'
                  AND indexname  = 'susu_memberships_user_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void insertMembership(UUID groupId, UUID userId, Integer position, String status) {
        jdbc.update("""
                INSERT INTO susu.susu_memberships
                    (id, susu_group_id, user_id, rotation_position, status, joined_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, now())
                """, groupId, userId, position, status);
    }

    private UUID createAdditionalGroup() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO susu.susu_groups
                    (id, organiser_user_id, name, contribution_amount,
                     frequency, target_member_count, status, join_code, created_at)
                VALUES (?, gen_random_uuid(), 'Extra Group', 5000, 'MONTHLY', 6, 'PENDING', ?, now())
                """, id, uniqueCode());
        return id;
    }

    private String uniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
