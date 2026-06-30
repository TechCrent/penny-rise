package com.stash.admin.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
 * Verifies the admin.admin_audit_actions migration (V21 SQL + V21_1 REVOKE):
 * - schema and table apply cleanly
 * - FK to admin_accounts enforced
 * - all three indexes present
 * - append-only: UPDATE and DELETE tests are disabled pending stash_app role
 *   infrastructure (see V21_1 and Step 7 of the v0.5-002 issue plan)
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminAuditActionsSchemaTest {

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

    private UUID superAdminId;

    @BeforeEach
    void setUp() {
        superAdminId = UUID.fromString(jdbc.queryForObject("""
                SELECT id::TEXT FROM admin.admin_accounts WHERE account_type = 'SUPER'
                """, String.class));
    }

    // ── Schema structure ────────────────────────────────────────────────

    @Test
    @DisplayName("admin_audit_actions has correct column count")
    void table_has_correct_columns() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'admin' AND table_name = 'admin_audit_actions'
                """, Integer.class);
        assertThat(count).isEqualTo(8);
    }

    @Test
    @DisplayName("all three indexes exist")
    void all_indexes_exist() {
        for (String idx : new String[]{
                "admin_audit_actions_admin_created_idx",
                "admin_audit_actions_target_created_idx",
                "admin_audit_actions_action_created_idx"
        }) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname = 'admin' AND tablename = 'admin_audit_actions'
                      AND indexname = ?
                    """, Integer.class, idx);
            assertThat(count).as("index %s should exist", idx).isEqualTo(1);
        }
    }

    // ── Happy insert ─────────────────────────────────────────────────────

    @Test
    @DisplayName("valid audit action row inserts with JSONB payload")
    void valid_insert() {
        assertThatCode(() -> jdbc.update("""
                INSERT INTO admin.admin_audit_actions
                    (id, admin_account_id, action_type, target_type, target_id,
                     payload, ip_address, created_at)
                VALUES (gen_random_uuid(), ?, 'USER_SUSPENDED', 'USER', gen_random_uuid(),
                        '{"reason": "fraud review"}'::jsonb, '203.0.113.42', now())
                """, superAdminId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NULL payload and ip_address are allowed")
    void nullable_columns_allowed() {
        assertThatCode(() -> jdbc.update("""
                INSERT INTO admin.admin_audit_actions
                    (id, admin_account_id, action_type, target_type, target_id, created_at)
                VALUES (gen_random_uuid(), ?, 'SYSTEM_ACTION', 'VAULT', gen_random_uuid(), now())
                """, superAdminId))
                .doesNotThrowAnyException();
    }

    // ── FK enforcement ───────────────────────────────────────────────────

    @Test
    @DisplayName("admin_account_id must reference an existing admin")
    void fk_enforced() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.admin_audit_actions
                    (id, admin_account_id, action_type, target_type, target_id, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'X', 'USER', gen_random_uuid(), now())
                """))
                .hasMessageContaining("admin_audit_actions_admin_fk");
    }

    // ── Append-only enforcement ──────────────────────────────────────────
    //
    // These tests require a JdbcTemplate connected as the stash_app role
    // (not the testcontainer superuser, which bypasses REVOKE). They are
    // disabled until the stash_app role and a matching test DataSource bean
    // are wired into the monolith's test infrastructure.
    //
    // To enable: create stash_app role in the testcontainer init script or
    // @DynamicPropertySource, configure a second DataSource bean authenticated
    // as stash_app, and replace the @Disabled tests below with ones that
    // use that bean. See v0.5-002 Step 7.

    @Test
    @Disabled("Requires stash_app-authenticated DataSource — see v0.5-002 Step 7")
    @DisplayName("UPDATE from stash_app role fails with permission denied")
    void update_denied_for_app_role() {
        // intentionally empty — re-enable once stash_app DataSource is wired
    }

    @Test
    @Disabled("Requires stash_app-authenticated DataSource — see v0.5-002 Step 7")
    @DisplayName("DELETE from stash_app role fails with permission denied")
    void delete_denied_for_app_role() {
        // intentionally empty — re-enable once stash_app DataSource is wired
    }

    @Test
    @Disabled("Requires stash_app-authenticated DataSource — see v0.5-002 Step 7")
    @DisplayName("INSERT from stash_app role still succeeds")
    void insert_still_allowed_for_app_role() {
        // intentionally empty — re-enable once stash_app DataSource is wired
    }

    @Test
    @Disabled("Requires stash_app-authenticated DataSource — see v0.5-002 Step 7")
    @DisplayName("SELECT from stash_app role still succeeds")
    void select_still_allowed_for_app_role() {
        // intentionally empty — re-enable once stash_app DataSource is wired
    }
}
