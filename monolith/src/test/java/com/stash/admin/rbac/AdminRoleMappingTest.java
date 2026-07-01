package com.stash.admin.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive matrix test for the DoD: "Tests cover every account_type
 * against every protected admin endpoint." Endpoints route through
 * AdminRoleMapping by AdminResource, so every (AdminAccountType,
 * AdminResource) pair tested here covers every current and future endpoint
 * that uses RequiresAdminResource correctly.
 */
class AdminRoleMappingTest {

    private final AdminRoleMapping roleMapping = new AdminRoleMapping();

    @ParameterizedTest(name = "SUPER has access to {0}")
    @EnumSource(AdminResource.class)
    @DisplayName("SUPER has full access to every admin resource")
    void superHasFullAccess(AdminResource resource) {
        assertThat(roleMapping.hasAccess(AdminAccountType.SUPER, null, resource)).isTrue();
    }

    @ParameterizedTest(name = "VICE_SUPER access to {0}")
    @EnumSource(AdminResource.class)
    @DisplayName("VICE_SUPER has access to everything except STAFF_MANAGEMENT")
    void viceSuperAccessMatrix(AdminResource resource) {
        boolean expected = resource != AdminResource.STAFF_MANAGEMENT;
        assertThat(roleMapping.hasAccess(AdminAccountType.VICE_SUPER, null, resource)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "TAB scoped to {0} sees only that resource")
    @EnumSource(AdminResource.class)
    @DisplayName("TAB admin is granted exactly the resource matching their role_name")
    void tabAccessMatrix(AdminResource ownedResource) {
        String roleName = ownedResource.name();
        for (AdminResource candidate : AdminResource.values()) {
            boolean expected = candidate == ownedResource;
            assertThat(roleMapping.hasAccess(AdminAccountType.TAB, roleName, candidate))
                    .as("TAB with role_name=%s checking %s", roleName, candidate)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("TAB admin with null or unrecognised role_name has no resource access")
    void tabWithNoUsableRoleNameHasNoAccess() {
        for (AdminResource resource : AdminResource.values()) {
            assertThat(roleMapping.hasAccess(AdminAccountType.TAB, null, resource)).isFalse();
            assertThat(roleMapping.hasAccess(AdminAccountType.TAB, "NOT_A_RESOURCE", resource)).isFalse();
        }
    }

    @Test
    @DisplayName("DoD scenario: TAB role_name=KYC reaches KYC and is rejected from DISPUTES")
    void kycTabbedAdminRejectedFromDisputes() {
        assertThat(roleMapping.hasAccess(AdminAccountType.TAB, "KYC", AdminResource.KYC)).isTrue();
        assertThat(roleMapping.hasAccess(AdminAccountType.TAB, "KYC", AdminResource.DISPUTES)).isFalse();
    }

    @Test
    @DisplayName("SUPER may create VICE_SUPER and TAB, but not another SUPER")
    void superCreationRights() {
        assertThat(roleMapping.canCreate(AdminAccountType.SUPER, AdminAccountType.VICE_SUPER)).isTrue();
        assertThat(roleMapping.canCreate(AdminAccountType.SUPER, AdminAccountType.TAB)).isTrue();
        assertThat(roleMapping.canCreate(AdminAccountType.SUPER, AdminAccountType.SUPER)).isFalse();
    }

    @Test
    @DisplayName("VICE_SUPER may create TAB only")
    void viceSuperCreationRights() {
        assertThat(roleMapping.canCreate(AdminAccountType.VICE_SUPER, AdminAccountType.TAB)).isTrue();
        assertThat(roleMapping.canCreate(AdminAccountType.VICE_SUPER, AdminAccountType.VICE_SUPER)).isFalse();
        assertThat(roleMapping.canCreate(AdminAccountType.VICE_SUPER, AdminAccountType.SUPER)).isFalse();
    }

    @ParameterizedTest(name = "TAB cannot create {0}")
    @EnumSource(AdminAccountType.class)
    @DisplayName("TAB may create no admin accounts of any type")
    void tabCreationRights(AdminAccountType target) {
        assertThat(roleMapping.canCreate(AdminAccountType.TAB, target)).isFalse();
    }
}
