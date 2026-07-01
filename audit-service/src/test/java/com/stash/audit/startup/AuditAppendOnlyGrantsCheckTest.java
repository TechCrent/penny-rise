package com.stash.audit.startup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditAppendOnlyGrantsCheckTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditAppendOnlyGrantsCheck check =
            new AuditAppendOnlyGrantsCheck(jdbcTemplate, "audit_app", "audit_dashboard_ro");

    @BeforeEach
    void setUp() {
        // Default: correctly configured grants; individual tests override as needed
        stubGrants("audit_app", "INSERT", "SELECT");
        stubGrants("audit_dashboard_ro", "SELECT");
    }

    @Test
    @DisplayName("correctly configured grants pass without throwing")
    void correctGrantsPass() {
        assertThatCode(() -> check.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("app role with UPDATE granted fails fast")
    void appRoleWithUpdateFailsFast() {
        stubGrants("audit_app", "INSERT", "SELECT", "UPDATE");

        assertThatThrownBy(() -> check.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UPDATE");
    }

    @Test
    @DisplayName("app role with DELETE granted fails fast")
    void appRoleWithDeleteFailsFast() {
        stubGrants("audit_app", "INSERT", "SELECT", "DELETE");

        assertThatThrownBy(() -> check.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELETE");
    }

    @Test
    @DisplayName("app role missing INSERT fails fast")
    void appRoleMissingInsertFailsFast() {
        stubGrants("audit_app", "SELECT");

        assertThatThrownBy(() -> check.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing INSERT");
    }

    @Test
    @DisplayName("app role missing SELECT fails fast")
    void appRoleMissingSelectFailsFast() {
        stubGrants("audit_app", "INSERT");

        assertThatThrownBy(() -> check.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing SELECT");
    }

    @Test
    @DisplayName("read-only role missing SELECT fails fast")
    void readOnlyRoleMissingSelectFailsFast() {
        stubGrants("audit_dashboard_ro"); // no privileges at all

        assertThatThrownBy(() -> check.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit_dashboard_ro is missing SELECT");
    }

    @Test
    @DisplayName("read-only role with an unexpected write privilege fails fast")
    void readOnlyRoleWithWriteFailsFast() {
        stubGrants("audit_dashboard_ro", "SELECT", "INSERT");

        assertThatThrownBy(() -> check.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("write privileges");
    }

    private void stubGrants(String role, String... privileges) {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class),
                eq("audit"), eq("audit_log_entries"), eq(role)))
                .thenReturn(List.of(privileges));
    }
}
