package com.stash.audit.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuditLogAccessGuardTest {

    AdminAuditLogAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AdminAuditLogAccessGuard();
    }

    @ParameterizedTest(name = "accountType={0} roleName={1} allowed={2}")
    @CsvSource({
            "SUPER,        , true",
            "VICE_SUPER,   , true",
            "TAB, AUDIT_LOG, true",
            "TAB,        , false",
            "TAB, OTHER_ROLE, false",
            "UNKNOWN_TYPE, , false"
    })
    void accessBasedOnAccountTypeAndRole(String accountType, String roleName, boolean allowed) {
        var claims = new AdminJwtVerifier.AdminTokenClaims(UUID.randomUUID(), accountType, roleName);
        var auth   = new UsernamePasswordAuthenticationToken(claims.adminAccountId(), null, List.of());
        auth.setDetails(claims);

        if (allowed) {
            assertThatCode(() -> guard.requireAccess(auth)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> guard.requireAccess(auth))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @ParameterizedTest(name = "nullAuth={0}")
    @CsvSource({"null", "no-auth"})
    void nullAuthThrows401(String scenario) {
        assertThatThrownBy(() -> guard.requireAccess(null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
