package com.stash.payments.shared.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class LedgerGrantsVerifierTest {

    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final LedgerGrantsVerifier verifier = new LedgerGrantsVerifier(jdbc);

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Stub the table-level grant query for a given schema.table */
    private void stubTableGrants(String schema, String table, List<String> privileges) {
        when(jdbc.queryForList(
                contains("role_table_grants"), eq(String.class),
                eq(LedgerGrantsVerifier.APP_ROLE), eq(schema), eq(table)))
                .thenReturn(privileges);
    }

    /** Stub the column-level grant query for a given schema.table */
    private void stubColumnGrants(String schema, String table, List<String> columns) {
        when(jdbc.queryForList(
                contains("role_column_grants"), eq(String.class),
                eq(LedgerGrantsVerifier.APP_ROLE), eq(schema), eq(table)))
                .thenReturn(columns);
    }

    private void stubAllClean() {
        // ledger.ledger_entries — no table-level mutating grants
        stubTableGrants("ledger", "ledger_entries", Collections.emptyList());
        // outbox.outbox_events — no table-level mutating grants
        stubTableGrants("outbox", "outbox_events", Collections.emptyList());
        // outbox.outbox_events — correct column-level grants
        stubColumnGrants("outbox", "outbox_events",
                List.of("status", "attempts", "sent_at", "last_attempted_at"));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("passes when all grants are correctly configured")
    void passes_on_clean_configuration() {
        stubAllClean();
        assertThatNoException().isThrownBy(verifier::verifyOnStartup);
    }

    // ── ledger.ledger_entries failures ────────────────────────────────────

    @Nested
    @DisplayName("ledger.ledger_entries")
    class LedgerEntriesChecks {

        @Test
        @DisplayName("fails when table-level UPDATE is present on ledger_entries")
        void fails_on_table_level_update() {
            stubTableGrants("ledger", "ledger_entries", List.of("UPDATE"));
            stubTableGrants("outbox", "outbox_events", Collections.emptyList());
            stubColumnGrants("outbox", "outbox_events",
                    List.of("status", "attempts", "sent_at", "last_attempted_at"));

            assertThatThrownBy(verifier::verifyOnStartup)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ledger.ledger_entries")
                    .hasMessageContaining("UPDATE");
        }

        @Test
        @DisplayName("fails when table-level DELETE is present on ledger_entries")
        void fails_on_table_level_delete() {
            stubTableGrants("ledger", "ledger_entries", List.of("DELETE"));
            stubTableGrants("outbox", "outbox_events", Collections.emptyList());
            stubColumnGrants("outbox", "outbox_events",
                    List.of("status", "attempts", "sent_at", "last_attempted_at"));

            assertThatThrownBy(verifier::verifyOnStartup)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ledger.ledger_entries");
        }
    }

    // ── outbox.outbox_events failures ─────────────────────────────────────

    @Nested
    @DisplayName("outbox.outbox_events")
    class OutboxEventsChecks {

        @Test
        @DisplayName("fails when table-level UPDATE is present on outbox_events")
        void fails_on_table_level_update() {
            stubTableGrants("ledger", "ledger_entries", Collections.emptyList());
            stubTableGrants("outbox", "outbox_events", List.of("UPDATE"));
            stubColumnGrants("outbox", "outbox_events",
                    List.of("status", "attempts", "sent_at", "last_attempted_at"));

            assertThatThrownBy(verifier::verifyOnStartup)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("outbox.outbox_events");
        }

        @Test
        @DisplayName("fails when an unexpected column has UPDATE (payload)")
        void fails_on_unexpected_column_update_grant() {
            stubTableGrants("ledger", "ledger_entries", Collections.emptyList());
            stubTableGrants("outbox", "outbox_events", Collections.emptyList());
            stubColumnGrants("outbox", "outbox_events",
                    List.of("status", "attempts", "sent_at", "last_attempted_at", "payload"));

            assertThatThrownBy(verifier::verifyOnStartup)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("payload")
                    .hasMessageContaining("append-only");
        }

        @Test
        @DisplayName("fails when a required relay column is missing UPDATE grant")
        void fails_when_relay_column_grant_missing() {
            stubTableGrants("ledger", "ledger_entries", Collections.emptyList());
            stubTableGrants("outbox", "outbox_events", Collections.emptyList());
            // 'attempts' missing — relay can't increment retry count
            stubColumnGrants("outbox", "outbox_events",
                    List.of("status", "sent_at", "last_attempted_at"));

            assertThatThrownBy(verifier::verifyOnStartup)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("attempts")
                    .hasMessageContaining("missing");
        }
    }
}
