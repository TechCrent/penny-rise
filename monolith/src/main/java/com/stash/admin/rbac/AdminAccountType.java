package com.stash.admin.rbac;

/**
 * Mirrors admin.admin_accounts.account_type (stored as VARCHAR, not a DB
 * enum — see Schema doc §2.1). This Java enum exists only inside the rbac
 * package so the mapping table gets type safety; everything that crosses
 * the JWT boundary (AdminJwtService.AdminTokenClaims.accountType()) stays a
 * plain String, same as v0.5-003.
 */
public enum AdminAccountType {
    SUPER, VICE_SUPER, TAB;

    public static AdminAccountType parse(String raw) {
        if (raw == null) return null;
        try {
            return AdminAccountType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
