package com.stash.admin.schema;

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
 * Verifies the admin.disputes migration (V22):
 * - table and columns apply cleanly
 * - status, priority, and type CHECK constraints enforced
 * - resolution consistency CHECKs (resolved_at/by only on terminal statuses)
 * - FK enforcement for assigned_to_admin_id and resolved_by_admin_id
 * - all four indexes present
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DisputesSchemaTest {

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
    @DisplayName("disputes table has correct column count")
    void table_has_correct_columns() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'admin' AND table_name = 'disputes'
                """, Integer.class);
        // id, raised_by_user_id, dispute_type, related_entity_type,
        // related_entity_id, subject, description, status, priority,
        // assigned_to_admin_id, resolution, resolved_at, resolved_by_admin_id,
        // created_at, updated_at = 15
        assertThat(count).isEqualTo(15);
    }

    @Test
    @DisplayName("status_priority composite index exists")
    void status_priority_index_exists() {
        assertIndexExists("disputes_status_priority_idx");
    }

    @Test
    @DisplayName("raised_by index exists")
    void raised_by_index_exists() {
        assertIndexExists("disputes_raised_by_idx");
    }

    @Test
    @DisplayName("assigned_to partial index exists")
    void assigned_to_index_exists() {
        assertIndexExists("disputes_assigned_to_idx");
    }

    @Test
    @DisplayName("related_entity composite index exists")
    void related_entity_index_exists() {
        assertIndexExists("disputes_related_entity_idx");
    }

    // ── Happy insert ─────────────────────────────────────────────────────

    @Test
    @DisplayName("valid OPEN dispute inserts with default status/priority")
    void valid_open_dispute_inserts() {
        assertThatCode(() ->
                insertDispute(UUID.randomUUID(), "TRANSACTION", "TRANSACTION",
                        UUID.randomUUID(), "Unauthorised transfer",
                        "I never made this transfer", "OPEN", "NORMAL"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("all four dispute_type values accepted")
    void all_dispute_types_accepted() {
        for (String type : new String[]{"TRANSACTION", "KYC", "SUSU", "OTHER"}) {
            assertThatCode(() ->
                    insertDispute(UUID.randomUUID(), type, "TRANSACTION",
                            UUID.randomUUID(), "Subject", "Description",
                            "OPEN", "NORMAL"))
                    .as("dispute_type=%s", type)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("invalid dispute_type violates CHECK")
    void invalid_dispute_type_fails() {
        assertThatThrownBy(() ->
                insertDispute(UUID.randomUUID(), "BILLING", "TRANSACTION",
                        UUID.randomUUID(), "Subject", "Description", "OPEN", "NORMAL"))
                .hasMessageContaining("disputes_type_check");
    }

    @Test
    @DisplayName("OPEN and IN_REVIEW statuses accepted without resolution metadata")
    void open_and_in_review_statuses_accepted() {
        assertThatCode(() ->
                insertDispute(UUID.randomUUID(), "OTHER", "USER",
                        UUID.randomUUID(), "S", "D", "OPEN", "LOW"))
                .doesNotThrowAnyException();

        assertThatCode(() ->
                insertDispute(UUID.randomUUID(), "OTHER", "USER",
                        UUID.randomUUID(), "S", "D", "IN_REVIEW", "LOW"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RESOLVED status with resolution metadata accepted")
    void resolved_status_with_metadata_accepted() {
        assertThatCode(() -> jdbc.update("""
                INSERT INTO admin.disputes
                    (id, raised_by_user_id, dispute_type, related_entity_type,
                     related_entity_id, subject, description, status, priority,
                     resolved_at, resolved_by_admin_id, created_at, updated_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'OTHER', 'USER',
                        gen_random_uuid(), 'S', 'D', 'RESOLVED', 'LOW',
                        now(), ?, now(), now())
                """, superAdminId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("invalid status violates CHECK")
    void invalid_status_fails() {
        assertThatThrownBy(() ->
                insertDispute(UUID.randomUUID(), "OTHER", "USER",
                        UUID.randomUUID(), "S", "D", "PENDING_REVIEW", "LOW"))
                .hasMessageContaining("disputes_status_check");
    }

    @Test
    @DisplayName("all four priority values accepted")
    void all_priority_values_accepted() {
        for (String p : new String[]{"LOW", "NORMAL", "HIGH", "CRITICAL"}) {
            assertThatCode(() ->
                    insertDispute(UUID.randomUUID(), "OTHER", "USER",
                            UUID.randomUUID(), "S", "D", "OPEN", p))
                    .as("priority=%s", p)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("invalid priority violates CHECK")
    void invalid_priority_fails() {
        assertThatThrownBy(() ->
                insertDispute(UUID.randomUUID(), "OTHER", "USER",
                        UUID.randomUUID(), "S", "D", "OPEN", "URGENT"))
                .hasMessageContaining("disputes_priority_check");
    }

    // ── Resolution metadata consistency CHECKs ────────────────────────────

    @Test
    @DisplayName("RESOLVED status without resolved_at violates terminal-metadata CHECK")
    void resolved_without_metadata_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.disputes
                    (id, raised_by_user_id, dispute_type, related_entity_type,
                     related_entity_id, subject, description, status, priority,
                     created_at, updated_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'OTHER', 'USER',
                        gen_random_uuid(), 'S', 'D', 'RESOLVED', 'NORMAL',
                        now(), now())
                """))
                .hasMessageContaining("disputes_terminal_requires_resolution_metadata");
    }

    @Test
    @DisplayName("resolved_at set on an OPEN dispute violates CHECK")
    void resolved_at_on_open_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.disputes
                    (id, raised_by_user_id, dispute_type, related_entity_type,
                     related_entity_id, subject, description, status, priority,
                     resolved_at, created_at, updated_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'OTHER', 'USER',
                        gen_random_uuid(), 'S', 'D', 'OPEN', 'NORMAL',
                        now(), now(), now())
                """))
                .hasMessageContaining("disputes_resolved_at_requires_terminal");
    }

    @Test
    @DisplayName("resolution JSONB on an OPEN dispute violates CHECK")
    void resolution_on_open_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.disputes
                    (id, raised_by_user_id, dispute_type, related_entity_type,
                     related_entity_id, subject, description, status, priority,
                     resolution, created_at, updated_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'OTHER', 'USER',
                        gen_random_uuid(), 'S', 'D', 'OPEN', 'NORMAL',
                        '{"category": "REFUNDED"}'::jsonb, now(), now())
                """))
                .hasMessageContaining("disputes_resolution_requires_terminal");
    }

    @Test
    @DisplayName("resolution JSONB stores and round-trips correctly on RESOLVED dispute")
    void resolution_jsonb_roundtrip() {
        UUID disputeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO admin.disputes
                    (id, raised_by_user_id, dispute_type, related_entity_type,
                     related_entity_id, subject, description, status, priority,
                     resolution, resolved_at, resolved_by_admin_id, created_at, updated_at)
                VALUES (?, gen_random_uuid(), 'TRANSACTION', 'TRANSACTION',
                        gen_random_uuid(), 'S', 'D', 'RESOLVED', 'HIGH',
                        '{"category": "REFUNDED", "notes": "Confirmed duplicate charge"}'::jsonb,
                        now(), ?, now(), now())
                """, disputeId, superAdminId);

        String category = jdbc.queryForObject("""
                SELECT resolution->>'category' FROM admin.disputes WHERE id = ?
                """, String.class, disputeId);
        assertThat(category).isEqualTo("REFUNDED");
    }

    // ── FK enforcement ───────────────────────────────────────────────────

    @Test
    @DisplayName("assigned_to_admin_id must reference an existing admin if set")
    void assigned_to_fk_enforced() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admin.disputes
                    (id, raised_by_user_id, dispute_type, related_entity_type,
                     related_entity_id, subject, description, status, priority,
                     assigned_to_admin_id, created_at, updated_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'OTHER', 'USER',
                        gen_random_uuid(), 'S', 'D', 'IN_REVIEW', 'NORMAL',
                        gen_random_uuid(), now(), now())
                """))
                .hasMessageContaining("disputes_assigned_to_fk");
    }

    @Test
    @DisplayName("NULL assigned_to_admin_id is valid (unassigned)")
    void null_assigned_to_valid() {
        assertThatCode(() ->
                insertDispute(UUID.randomUUID(), "OTHER", "USER",
                        UUID.randomUUID(), "S", "D", "OPEN", "NORMAL"))
                .doesNotThrowAnyException();
    }

    // ── Required field CHECKs ──────────────────────────────────────────────

    @Test
    @DisplayName("blank subject violates CHECK")
    void blank_subject_fails() {
        assertThatThrownBy(() ->
                insertDispute(UUID.randomUUID(), "OTHER", "USER",
                        UUID.randomUUID(), "   ", "D", "OPEN", "NORMAL"))
                .hasMessageContaining("disputes_subject_not_blank");
    }

    @Test
    @DisplayName("blank description violates CHECK")
    void blank_description_fails() {
        assertThatThrownBy(() ->
                insertDispute(UUID.randomUUID(), "OTHER", "USER",
                        UUID.randomUUID(), "S", "   ", "OPEN", "NORMAL"))
                .hasMessageContaining("disputes_description_not_blank");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void insertDispute(UUID raisedBy, String type, String relatedType,
                               UUID relatedId, String subject, String description,
                               String status, String priority) {
        jdbc.update("""
                INSERT INTO admin.disputes
                    (id, raised_by_user_id, dispute_type, related_entity_type,
                     related_entity_id, subject, description, status, priority,
                     created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """, raisedBy, type, relatedType, relatedId, subject, description,
                status, priority);
    }

    private void assertIndexExists(String indexName) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'admin' AND tablename = 'disputes'
                  AND indexname = ?
                """, Integer.class, indexName);
        assertThat(count).as("index %s should exist", indexName).isEqualTo(1);
    }
}
