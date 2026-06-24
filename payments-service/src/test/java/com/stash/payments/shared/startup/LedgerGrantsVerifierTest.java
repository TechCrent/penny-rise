package com.stash.payments.shared.startup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class LedgerGrantsVerifierTest {

    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final LedgerGrantsVerifier verifier = new LedgerGrantsVerifier(jdbc);

    @Test
    @DisplayName("passes when no UPDATE or DELETE grants are found")
    void passes_when_no_forbidden_grants() {
        when(jdbc.queryForList(any(String.class), eq(String.class),
                eq(LedgerGrantsVerifier.APP_ROLE),
                eq("ledger"),
                eq("ledger_entries")))
                .thenReturn(Collections.emptyList());

        assertThatNoException().isThrownBy(verifier::verifyOnStartup);
    }

    @Test
    @DisplayName("throws IllegalStateException when UPDATE grant is found")
    void fails_when_update_grant_present() {
        when(jdbc.queryForList(any(String.class), eq(String.class),
                eq(LedgerGrantsVerifier.APP_ROLE),
                eq("ledger"),
                eq("ledger_entries")))
                .thenReturn(List.of("UPDATE"));

        assertThatThrownBy(verifier::verifyOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Append-only violation")
                .hasMessageContaining("ledger.ledger_entries")
                .hasMessageContaining("UPDATE");
    }

    @Test
    @DisplayName("throws IllegalStateException when DELETE grant is found")
    void fails_when_delete_grant_present() {
        when(jdbc.queryForList(any(String.class), eq(String.class),
                eq(LedgerGrantsVerifier.APP_ROLE),
                eq("ledger"),
                eq("ledger_entries")))
                .thenReturn(List.of("DELETE"));

        assertThatThrownBy(verifier::verifyOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Append-only violation")
                .hasMessageContaining("DELETE");
    }

    @Test
    @DisplayName("error message contains enough detail to diagnose the problem")
    void error_message_is_diagnostic() {
        when(jdbc.queryForList(any(String.class), eq(String.class),
                eq(LedgerGrantsVerifier.APP_ROLE),
                eq("ledger"),
                eq("ledger_entries")))
                .thenReturn(List.of("UPDATE", "DELETE"));

        assertThatThrownBy(verifier::verifyOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(LedgerGrantsVerifier.APP_ROLE)
                .hasMessageContaining("Refusing to start");
    }
}
