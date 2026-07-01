package com.stash.admin.rbac;

import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAccessEvaluatorTest {

    private final AdminAccessEvaluator evaluator = new AdminAccessEvaluator(new AdminRoleMapping());

    @Test
    @DisplayName("check() reads claims from Authentication.getDetails(), not the principal")
    void readsClaimsFromDetails() {
        AdminTokenClaims claims = new AdminTokenClaims(UUID.randomUUID(), "SUPER", null);
        MethodSecurityExpressionOperations root = rootWithDetails(claims);

        assertThat(evaluator.check(root, AdminResource.STAFF_MANAGEMENT)).isTrue();
    }

    @Test
    @DisplayName("check() returns false when details isn't an AdminTokenClaims (e.g. unauthenticated)")
    void returnsFalseForMissingClaims() {
        MethodSecurityExpressionOperations root = rootWithDetails(null);

        assertThat(evaluator.check(root, AdminResource.KYC)).isFalse();
    }

    @Test
    @DisplayName("canCreate() honours the hierarchy via the same claims source")
    void canCreateReadsClaims() {
        AdminTokenClaims viceSuper = new AdminTokenClaims(UUID.randomUUID(), "VICE_SUPER", null);
        MethodSecurityExpressionOperations root = rootWithDetails(viceSuper);

        assertThat(evaluator.canCreate(root, "TAB")).isTrue();
        assertThat(evaluator.canCreate(root, "SUPER")).isFalse();
    }

    private MethodSecurityExpressionOperations rootWithDetails(Object details) {
        Authentication auth = mock(Authentication.class);
        when(auth.getDetails()).thenReturn(details);
        MethodSecurityExpressionOperations root = mock(MethodSecurityExpressionOperations.class);
        when(root.getAuthentication()).thenReturn(auth);
        return root;
    }
}
