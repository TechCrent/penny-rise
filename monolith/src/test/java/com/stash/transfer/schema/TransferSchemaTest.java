package com.stash.transfer.schema;

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
 * Verifies the transfer schema migrations (V17).
 * Covers §4.1 peer_transfers and §4.2 monthly_transfer_quotas.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransferSchemaTest {

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

    // ── Schema and table existence ─────────────────────────────────────────

    @Test
    @DisplayName("transfer schema exists")
    void transfer_schema_exists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'transfer'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("peer_transfers table has correct column count (12)")
    void peer_transfers_column_count() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'transfer' AND table_name = 'peer_transfers'
                """, Integer.class);
        assertThat(count).isEqualTo(12);
    }

    @Test
    @DisplayName("monthly_transfer_quotas table has correct column count (6)")
    void monthly_transfer_quotas_column_count() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'transfer' AND table_name = 'monthly_transfer_quotas'
                """, Integer.class);
        assertThat(count).isEqualTo(6);
    }

    // ── peer_transfers: happy inserts ──────────────────────────────────────

    @Test
    @DisplayName("valid PENDING peer transfer inserts")
    void valid_pending_transfer_inserts() {
        UUID senderId    = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        String idemKey   = UUID.randomUUID().toString();

        assertThatCode(() -> jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, created_at)
                VALUES (gen_random_uuid(), ?, ?, 50000, 0, 'PENDING', ?, now())
                """, senderId, recipientId, idemKey))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("COMPLETED transfer with completed_at and transaction_id inserts")
    void completed_transfer_inserts() {
        UUID txnId = UUID.randomUUID();
        assertThatCode(() -> jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, transaction_id,
                     counted_against_free_quota, completed_at, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                        50000, 0, 'COMPLETED', ?, ?, true, now(), now())
                """, UUID.randomUUID().toString(), txnId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("paid-tier transfer with fee_amount = 200 inserts")
    void paid_tier_transfer_inserts() {
        assertThatCode(() -> jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                        50000, 200, 'PENDING', ?, now())
                """, UUID.randomUUID().toString()))
                .doesNotThrowAnyException();
    }

    // ── peer_transfers: constraints ────────────────────────────────────────

    @Test
    @DisplayName("duplicate idempotency_key violates UNIQUE constraint")
    void duplicate_idempotency_key_fails() {
        String idemKey = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                        50000, 0, 'PENDING', ?, now())
                """, idemKey);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                        50000, 0, 'PENDING', ?, now())
                """, idemKey))
                .hasMessageContaining("peer_transfers_idempotency_key_uk");
    }

    @Test
    @DisplayName("amount = 0 violates CHECK (must be > 0)")
    void zero_amount_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                        0, 0, 'PENDING', ?, now())
                """, UUID.randomUUID().toString()))
                .hasMessageContaining("peer_transfers_amount_positive");
    }

    @Test
    @DisplayName("sender = recipient violates CHECK")
    void sender_equals_recipient_fails() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, created_at)
                VALUES (gen_random_uuid(), ?, ?, 50000, 0, 'PENDING', ?, now())
                """, userId, userId, UUID.randomUUID().toString()))
                .hasMessageContaining("peer_transfers_sender_not_recipient");
    }

    @Test
    @DisplayName("invalid status violates CHECK")
    void invalid_status_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                        50000, 0, 'REVERSED', ?, now())
                """, UUID.randomUUID().toString()))
                .hasMessageContaining("peer_transfers_status_check");
    }

    @Test
    @DisplayName("completed_at on PENDING violates CHECK")
    void completed_at_on_pending_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, completed_at, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                        50000, 0, 'PENDING', ?, now(), now())
                """, UUID.randomUUID().toString()))
                .hasMessageContaining("peer_transfers_completed_at_terminal");
    }

    @Test
    @DisplayName("negative fee_amount violates CHECK")
    void negative_fee_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.peer_transfers
                    (id, sender_user_id, recipient_user_id, amount, fee_amount,
                     status, idempotency_key, created_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                        50000, -1, 'PENDING', ?, now())
                """, UUID.randomUUID().toString()))
                .hasMessageContaining("peer_transfers_fee_non_negative");
    }

    // ── monthly_transfer_quotas: happy inserts ─────────────────────────────

    @Test
    @DisplayName("valid quota row inserts (June 2026)")
    void valid_quota_row_inserts() {
        assertThatCode(() -> jdbc.update("""
                INSERT INTO transfer.monthly_transfer_quotas
                    (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                VALUES (gen_random_uuid(), gen_random_uuid(), 2026, 6, 0, 0)
                """))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("quota row with max free_transfers_used = 5 inserts")
    void max_free_quota_inserts() {
        assertThatCode(() -> jdbc.update("""
                INSERT INTO transfer.monthly_transfer_quotas
                    (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                VALUES (gen_random_uuid(), gen_random_uuid(), 2026, 6, 5, 3)
                """))
                .doesNotThrowAnyException();
    }

    // ── monthly_transfer_quotas: constraints ───────────────────────────────

    @Test
    @DisplayName("duplicate (user_id, year, month) violates UNIQUE constraint")
    void duplicate_user_month_fails() {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO transfer.monthly_transfer_quotas
                    (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                VALUES (gen_random_uuid(), ?, 2026, 6, 0, 0)
                """, userId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.monthly_transfer_quotas
                    (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                VALUES (gen_random_uuid(), ?, 2026, 6, 1, 0)
                """, userId))
                .hasMessageContaining("monthly_transfer_quotas_user_month_uk");
    }

    @Test
    @DisplayName("month = 0 violates CHECK (valid range 1-12)")
    void month_zero_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.monthly_transfer_quotas
                    (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                VALUES (gen_random_uuid(), gen_random_uuid(), 2026, 0, 0, 0)
                """))
                .hasMessageContaining("monthly_transfer_quotas_month_valid");
    }

    @Test
    @DisplayName("month = 13 violates CHECK (valid range 1-12)")
    void month_thirteen_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.monthly_transfer_quotas
                    (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                VALUES (gen_random_uuid(), gen_random_uuid(), 2026, 13, 0, 0)
                """))
                .hasMessageContaining("monthly_transfer_quotas_month_valid");
    }

    @Test
    @DisplayName("month = 1 and month = 12 are valid (boundaries)")
    void month_boundaries_valid() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> {
            jdbc.update("""
                    INSERT INTO transfer.monthly_transfer_quotas
                        (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                    VALUES (gen_random_uuid(), ?, 2026, 1, 0, 0)
                    """, userId);
            jdbc.update("""
                    INSERT INTO transfer.monthly_transfer_quotas
                        (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                    VALUES (gen_random_uuid(), ?, 2026, 12, 0, 0)
                    """, userId);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("negative free_transfers_used violates CHECK")
    void negative_free_used_fails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer.monthly_transfer_quotas
                    (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                VALUES (gen_random_uuid(), gen_random_uuid(), 2026, 6, -1, 0)
                """))
                .hasMessageContaining("monthly_transfer_quotas_free_used_non_negative");
    }

    @Test
    @DisplayName("same user, different months: two rows allowed")
    void same_user_different_months_allowed() {
        UUID userId = UUID.randomUUID();
        assertThatCode(() -> {
            jdbc.update("""
                    INSERT INTO transfer.monthly_transfer_quotas
                        (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                    VALUES (gen_random_uuid(), ?, 2026, 6, 3, 0)
                    """, userId);
            jdbc.update("""
                    INSERT INTO transfer.monthly_transfer_quotas
                        (id, user_id, year, month, free_transfers_used, paid_transfers_count)
                    VALUES (gen_random_uuid(), ?, 2026, 7, 0, 0)
                    """, userId);
        }).doesNotThrowAnyException();
    }

    // ── Indexes ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("peer_transfers_sender_idx exists")
    void sender_idx_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'transfer'
                  AND tablename  = 'peer_transfers'
                  AND indexname  = 'peer_transfers_sender_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("peer_transfers_recipient_idx exists")
    void recipient_idx_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'transfer'
                  AND tablename  = 'peer_transfers'
                  AND indexname  = 'peer_transfers_recipient_idx'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("peer_transfers_idempotency_key_uk index exists (from unique constraint)")
    void idempotency_key_idx_exists() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'transfer'
                  AND tablename  = 'peer_transfers'
                  AND indexname  = 'peer_transfers_idempotency_key_uk'
                """, Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
