package com.stash.admin.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These endpoints are gated by an explicit {@code @PreAuthorize} SpEL expression
 * rather than {@code @RequiresAdminResource} — the project runs Spring Boot 3.3
 * (Spring Security 6.3), which lacks the meta-annotation placeholder substitution
 * {@code @RequiresAdminResource} needs (see its javadoc). Once the project upgrades
 * to Spring Boot 3.4+, these endpoints and this test should switch to
 * {@code @RequiresAdminResource(AdminResource.USER_MANAGEMENT)}.
 */
@DisplayName("AdminUserMutationController RBAC annotations")
class AdminUserMutationControllerRbacTest {

    @ParameterizedTest(name = "{0} requires USER_MANAGEMENT")
    @ValueSource(strings = {"suspend", "restore", "forceLogout"})
    @DisplayName("all three mutation endpoints require USER_MANAGEMENT")
    void allMutationEndpointsRequireUserManagement(String methodName) {
        var method = Arrays.stream(AdminUserMutationController.class.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow();
        var annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("AdminResource).USER_MANAGEMENT");
    }
}
