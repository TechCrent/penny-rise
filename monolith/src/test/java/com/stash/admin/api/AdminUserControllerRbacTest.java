package com.stash.admin.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These endpoints are gated by an explicit {@code @PreAuthorize} SpEL expression
 * rather than {@code @RequiresAdminResource} — the project runs Spring Boot 3.3
 * (Spring Security 6.3), which lacks the meta-annotation placeholder substitution
 * {@code @RequiresAdminResource} needs (see its javadoc). Once the project upgrades
 * to Spring Boot 3.4+, these endpoints and this test should switch to
 * {@code @RequiresAdminResource(AdminResource.USER_MANAGEMENT)}.
 */
@DisplayName("AdminUserController RBAC annotations")
class AdminUserControllerRbacTest {

    @Test
    @DisplayName("listUsers is gated to USER_MANAGEMENT")
    void listUsersIsProtected() throws NoSuchMethodException {
        var method = AdminUserController.class.getMethod(
                "listUsers", String.class, String.class, String.class, int.class, int.class);
        var annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("AdminResource).USER_MANAGEMENT");
    }

    @Test
    @DisplayName("getUserDetail is gated to USER_MANAGEMENT")
    void getUserDetailIsProtected() throws NoSuchMethodException {
        var method = AdminUserController.class.getMethod("getUserDetail", UUID.class);
        var annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("AdminResource).USER_MANAGEMENT");
    }
}
