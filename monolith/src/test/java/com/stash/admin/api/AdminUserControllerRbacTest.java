package com.stash.admin.api;

import com.stash.admin.rbac.AdminResource;
import com.stash.admin.rbac.RequiresAdminResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdminUserController RBAC annotations")
class AdminUserControllerRbacTest {

    @Test
    @DisplayName("listUsers is annotated with @RequiresAdminResource(USER_MANAGEMENT)")
    void listUsersIsProtected() throws NoSuchMethodException {
        var method = AdminUserController.class.getMethod(
                "listUsers", String.class, String.class, String.class, int.class, int.class);
        var annotation = method.getAnnotation(RequiresAdminResource.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(AdminResource.USER_MANAGEMENT);
    }

    @Test
    @DisplayName("getUserDetail is annotated with @RequiresAdminResource(USER_MANAGEMENT)")
    void getUserDetailIsProtected() throws NoSuchMethodException {
        var method = AdminUserController.class.getMethod("getUserDetail", UUID.class);
        var annotation = method.getAnnotation(RequiresAdminResource.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(AdminResource.USER_MANAGEMENT);
    }
}
