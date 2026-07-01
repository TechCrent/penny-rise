package com.stash.admin.rbac;

/**
 * String constants for admin_audit_actions.action_type, matching the inline
 * string convention used by AdminAuthService (ADMIN_LOGIN_SUCCESS, etc.).
 *
 * <p>USER_SUSPENDED is explicitly named in Schema doc §2.2's comment as an
 * example action_type, confirming this naming convention.
 */
public final class AdminAuditActionType {

    private AdminAuditActionType() {}

    public static final String USER_SUSPENDED    = "USER_SUSPENDED";
    public static final String USER_RESTORED     = "USER_RESTORED";
    public static final String USER_FORCE_LOGOUT = "USER_FORCE_LOGOUT";
}
