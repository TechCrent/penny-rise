package com.stash.admin.service;

import com.stash.admin.rbac.AdminAccountType;
import com.stash.admin.rbac.AdminRoleMapping;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Enforces the admin-account creation hierarchy (SUPER -> VICE_SUPER|TAB;
 * VICE_SUPER -> TAB; TAB -> nothing). Error convention matches
 * AdminAuthService: ResponseStatusException with a "CODE: message" reason.
 *
 * TODO: wire AdminAccountRepository / an Entity.create(...) factory method
 * from v0.5-001 once this issue's RBAC scaffolding is reviewed — left out
 * here to keep this issue scoped to authorization, not persistence.
 */
@Service
public class AdminAccountCreationService {

    private final AdminRoleMapping roleMapping;

    public AdminAccountCreationService(AdminRoleMapping roleMapping) {
        this.roleMapping = roleMapping;
    }

    public void assertCanCreate(AdminTokenClaims creator, String targetAccountTypeRaw) {
        AdminAccountType creatorType = AdminAccountType.parse(creator.accountType());
        AdminAccountType targetType  = AdminAccountType.parse(targetAccountTypeRaw);

        if (creatorType == null || targetType == null || !roleMapping.canCreate(creatorType, targetType)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "ADMIN_INSUFFICIENT_ROLE: " + creator.accountType() +
                    " cannot create an admin account of type " + targetAccountTypeRaw);
        }
    }
}
