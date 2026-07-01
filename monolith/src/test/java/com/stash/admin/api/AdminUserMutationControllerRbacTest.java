package com.stash.admin.api;

import com.stash.admin.rbac.AdminResource;
import com.stash.admin.rbac.RequiresAdminResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdminUserMutationController RBAC annotations")
class AdminUserMutationControllerRbacTest {

    @ParameterizedTest(name = "{0} requires USER_MANAGEMENT")
    @ValueSource(strings = {"suspend", "restore", "forceLogout"})
    @DisplayName("all three mutation endpoints require USER_MANAGEMENT")
    void allMutationEndpointsRequireUserManagement(String methodName) {
        var method = Arrays.stream(AdminUserMutationController.class.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow();
        var annotation = method.getAnnotation(RequiresAdminResource.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(AdminResource.USER_MANAGEMENT);
    }
}
