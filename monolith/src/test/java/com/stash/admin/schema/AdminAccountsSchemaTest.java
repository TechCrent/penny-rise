package com.stash.admin.schema;

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
 * Verifies the admin.admin_accounts migration (V20 SQL + V20_1 Java bootstrap):
 * - schema and table apply cleanly
 * - partial unique index enforces exactly one SUPER row
 * - bootstrap SUPER admin is created from env vars (injected via Maven surefire
 *   {@code <environmentVariables>} in pom.xml — BOOTSTRAP_ADMIN_EMAIL and
 *   BOOTSTRAP_ADMIN_PASSWORD must be set for the bootstrap tests to pass)
 * - all CHECK constraints behave as documented
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminAccountsSchemaTest {

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

    // ── Schema and table structure ──────────────────────────────────────

    @Test
    @DisplayName("admin schema exists")
    void admin_schema_exists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'admin'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("admin_accounts table has correct column count")
    void table_has_correct_columns() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'admin' AND table_name = 'admin_accounts'
                """, Integer.class);
        assertThat(count).isEqualTo(10);
    }

    // ── Bootstrap SUPER admin ───────────────────────────────────────────

    @Test
    @DisplayName("bootstrap SUPER admin exists after migration")
    void bootstrap_super_admin_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin.admin_accounts
                WHERE account_type = 'SUPER'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("bootstrap SUPER admin has created_by_id = NULL")
    void bootstrap_super_has_null_created_by() {
        String createdBy = jdbc.queryForObject("""
                SELECT created_by_id::TEXT FROM admin.admin_accounts
                WHERE account_type = 'SUPER'
                """, String.class);
        assertThat(createdBy).isNull();
    }

    @Test
    @DisplayName("bootstrap SUPER admin password_hash is BCrypt-formatted, not plaintext")
    void bootstrap_super_password_is_hashed() {
        String hash = jdbc.queryForObject("""
                SELECT password_hash FROM admin.admin_accounts
                WHERE account_type = 'SUPER'
                """, String.class);
        assertThat(hash).matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }

    @Test
    @DisplayName("bootstrap SUPER admin is_active = true")
    void bootstrap_super_is_active() {
        Boolean active = jdbc.queryForObject("""
                SELECT is_active FROM admin.admin_accounts
                WHERE account_type = 'SUPER'
                """, Boolean.class);
        assertThat(active).isTrue();
    }

    // ── Single-SUPER partial unique index ──────────────────────────────

    @Test
    @DisplayName("inserting a second SUPER row violates the partial unique index")
    void second_super_row_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, created_at)
                VALUES (gen_random_uuid(), 'second-super@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'Second Super', 'SUPER', NULL, true, now())
                """))
                .hasMessageContaining("admin_accounts_single_super_uk");
    }

    @Test
    @DisplayName("inserting a VICE_SUPER row alongside the existing SUPER succeeds")
    void vice_super_alongside_super_succeeds() {
        UUID superId = getBootstrapSuperId();

        assertThatCode(() -> jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, created_at)
                VALUES (gen_random_uuid(), 'vice@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'Vice Admin', 'VICE_SUPER', ?, true, now())
                """, superId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("multiple TAB rows are allowed (no uniqueness constraint on TAB)")
    void multiple_tab_rows_allowed() {
        UUID superId = getBootstrapSuperId();

        assertThatCode(() -> {
            jdbc.update("""
                    INSERT INTO admin.admin_accounts
                        (id, email, password_hash, full_name, role_name,
                         account_type, created_by_id, is_active, created_at)
                    VALUES (gen_random_uuid(), 'tab1@stash.app',
                            '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                            'Tab One', 'KYC_REVIEWER', 'TAB', ?, true, now())
                    """, superId);
            jdbc.update("""
                    INSERT INTO admin.admin_accounts
                        (id, email, password_hash, full_name, role_name,
                         account_type, created_by_id, is_active, created_at)
                    VALUES (gen_random_uuid(), 'tab2@stash.app',
                            '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                            'Tab Two', 'DISPUTE_HANDLER', 'TAB', ?, true, now())
                    """, superId);
        }).doesNotThrowAnyException();
    }

    // ── account_type CHECK ───────────────────────────────────────────────

    @Test
    @DisplayName("invalid account_type violates CHECK constraint")
    void invalid_account_type_fails() {
        UUID superId = getBootstrapSuperId();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, created_at)
                VALUES (gen_random_uuid(), 'bad@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'Bad Type', 'ADMIN', ?, true, now())
                """, superId))
                .hasMessageContaining("admin_accounts_type_check");
    }

    // ── created_by_id requirement ─────────────────────────────────────────

    @Test
    @DisplayName("non-SUPER row with NULL created_by_id violates CHECK")
    void non_super_requires_created_by() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, created_at)
                VALUES (gen_random_uuid(), 'orphan@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'Orphan Admin', 'VICE_SUPER', NULL, true, now())
                """))
                .hasMessageContaining("admin_accounts_created_by_required_unless_bootstrap");
    }

    // ── deactivated_at consistency ────────────────────────────────────────

    @Test
    @DisplayName("is_active=false with NULL deactivated_at violates CHECK")
    void inactive_without_deactivated_at_fails() {
        UUID superId = getBootstrapSuperId();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, deactivated_at, created_at)
                VALUES (gen_random_uuid(), 'inactive@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'Inactive Admin', 'TAB', ?, false, NULL, now())
                """, superId))
                .hasMessageContaining("admin_accounts_deactivated_at_consistency");
    }

    @Test
    @DisplayName("is_active=true with non-NULL deactivated_at violates CHECK")
    void active_with_deactivated_at_fails() {
        UUID superId = getBootstrapSuperId();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, deactivated_at, created_at)
                VALUES (gen_random_uuid(), 'inconsistent@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'Inconsistent Admin', 'TAB', ?, true, now(), now())
                """, superId))
                .hasMessageContaining("admin_accounts_deactivated_at_consistency");
    }

    @Test
    @DisplayName("is_active=false with deactivated_at set is valid")
    void inactive_with_deactivated_at_valid() {
        UUID superId = getBootstrapSuperId();

        assertThatCode(() -> jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, deactivated_at, created_at)
                VALUES (gen_random_uuid(), 'properly-deactivated@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'Properly Deactivated', 'TAB', ?, false, now(), now())
                """, superId))
                .doesNotThrowAnyException();
    }

    // ── email uniqueness ─────────────────────────────────────────────────

    @Test
    @DisplayName("duplicate email violates UNIQUE constraint")
    void duplicate_email_fails() {
        UUID superId = getBootstrapSuperId();

        jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, created_at)
                VALUES (gen_random_uuid(), 'dup@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'First', 'TAB', ?, true, now())
                """, superId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, created_at)
                VALUES (gen_random_uuid(), 'dup@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'Second', 'TAB', ?, true, now())
                """, superId))
                .hasMessageContaining("admin_accounts_email_uk");
    }

    // ── Indexes ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("single-SUPER partial unique index exists")
    void single_super_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'admin'
                  AND tablename  = 'admin_accounts'
                  AND indexname  = 'admin_accounts_single_super_uk'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("type_active composite index exists")
    void type_active_index_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'admin'
                  AND tablename  = 'admin_accounts'
                  AND indexname  = 'admin_accounts_type_active_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ── FK self-reference ────────────────────────────────────────────────

    @Test
    @DisplayName("created_by_id references a valid admin_accounts row")
    void created_by_fk_enforced() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.admin_accounts
                    (id, email, password_hash, full_name, account_type,
                     created_by_id, is_active, created_at)
                VALUES (gen_random_uuid(), 'badref@stash.app',
                        '$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ',
                        'Bad Ref', 'TAB', gen_random_uuid(), true, now())
                """))
                .hasMessageContaining("admin_accounts_created_by_fk");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID getBootstrapSuperId() {
        return UUID.fromString(jdbc.queryForObject("""
                SELECT id::TEXT FROM admin.admin_accounts WHERE account_type = 'SUPER'
                """, String.class));
    }
}
