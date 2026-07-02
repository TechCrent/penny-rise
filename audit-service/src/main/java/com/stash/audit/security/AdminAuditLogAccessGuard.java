package com.stash.audit.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Audit-service only gates one resource, so this is a plain switch rather than
 * a full AdminRoleMapping/AdminResource registry like the monolith's (v0.5-004).
 * If a second protected endpoint is ever added, port the monolith's fuller
 * pattern instead of letting ad hoc checks multiply.
 */
@Component
public class AdminAuditLogAccessGuard {

    private static final String REQUIRED_TAB_ROLE_NAME = "AUDIT_LOG";

    public void requireAccess(Authentication authentication) {
        if (authentication == null
                || !(authentication.getDetails() instanceof AdminJwtVerifier.AdminTokenClaims claims)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "AUTH_REQUIRED: A valid admin access token is required.");
        }

        boolean allowed = switch (claims.accountType()) {
            case "SUPER", "VICE_SUPER" -> true;
            case "TAB" -> REQUIRED_TAB_ROLE_NAME.equals(claims.roleName());
            default -> false;
        };

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "ADMIN_INSUFFICIENT_ROLE: Audit log access requires SUPER, VICE_SUPER, or TAB with role_name=AUDIT_LOG.");
        }
    }
}
