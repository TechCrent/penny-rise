package com.stash.admin.service;

import com.stash.admin.rbac.AdminRoleMapping;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.FORBIDDEN;

class AdminAccountCreationServiceTest {

    private AdminAccountCreationService service;

    @BeforeEach
    void setUp() {
        service = new AdminAccountCreationService(new AdminRoleMapping());
    }

    @Test
    @DisplayName("VICE_SUPER creating a TAB succeeds")
    void viceSuperCreatesTab() {
        var claims = new AdminTokenClaims(UUID.randomUUID(), "VICE_SUPER", null);
        assertThatCode(() -> service.assertCanCreate(claims, "TAB")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("VICE_SUPER creating a VICE_SUPER returns 403 ADMIN_INSUFFICIENT_ROLE")
    void viceSuperCannotCreateViceSuper() {
        var claims = new AdminTokenClaims(UUID.randomUUID(), "VICE_SUPER", null);
        assertThatThrownBy(() -> service.assertCanCreate(claims, "VICE_SUPER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(FORBIDDEN);
                    assertThat(e.getReason()).contains("ADMIN_INSUFFICIENT_ROLE");
                });
    }

    @Test
    @DisplayName("TAB creating anyone is rejected")
    void tabCannotCreateAnyone() {
        var claims = new AdminTokenClaims(UUID.randomUUID(), "TAB", "KYC");
        assertThatThrownBy(() -> service.assertCanCreate(claims, "TAB"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("unparseable target accountType is rejected, not NPE'd")
    void garbageTargetTypeRejected() {
        var claims = new AdminTokenClaims(UUID.randomUUID(), "SUPER", null);
        assertThatThrownBy(() -> service.assertCanCreate(claims, "NOT_A_TYPE"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
