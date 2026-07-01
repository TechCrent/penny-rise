package com.stash.admin.rbac;

import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.stereotype.Component;

/**
 * Bridges @PreAuthorize SpEL to AdminRoleMapping. Reads AdminTokenClaims from
 * Authentication.getDetails() — that's where AdminJwtAuthenticationFilter
 * (v0.5-003) puts them; the principal itself is just the admin's UUID.
 */
@Component("adminAccessEvaluator")
public class AdminAccessEvaluator {

    private final AdminRoleMapping roleMapping;

    public AdminAccessEvaluator(AdminRoleMapping roleMapping) {
        this.roleMapping = roleMapping;
    }

    /** Backing call for {@link RequiresAdminResource}. */
    public boolean check(MethodSecurityExpressionOperations root, AdminResource resource) {
        AdminTokenClaims claims = claimsOf(root);
        if (claims == null) return false;
        AdminAccountType accountType = AdminAccountType.parse(claims.accountType());
        return accountType != null && roleMapping.hasAccess(accountType, claims.roleName(), resource);
    }

    /**
     * Backing call for account-creation endpoints, where the decision depends
     * on a request-body field (the target account_type) rather than a fixed
     * resource — see AdminStaffController.
     */
    public boolean canCreate(MethodSecurityExpressionOperations root, String targetAccountTypeRaw) {
        AdminTokenClaims claims = claimsOf(root);
        if (claims == null) return false;
        AdminAccountType creator = AdminAccountType.parse(claims.accountType());
        AdminAccountType target  = AdminAccountType.parse(targetAccountTypeRaw);
        return creator != null && target != null && roleMapping.canCreate(creator, target);
    }

    private AdminTokenClaims claimsOf(MethodSecurityExpressionOperations root) {
        Object details = root.getAuthentication().getDetails();
        return details instanceof AdminTokenClaims claims ? claims : null;
    }
}
