package com.stash.admin.rbac;

/**
 * The closed set of admin functional areas. Every protected admin endpoint
 * declares exactly one of these via {@link RequiresAdminResource}. Add a
 * resource here and grant it in {@link AdminRoleMapping} — nowhere else.
 *
 * Maps to Stash_Wireframes.docx: USER_MANAGEMENT (A3/A4), KYC (A5/A6),
 * DISPUTES (A7/A8), AUDIT_LOG (A9), STAFF_MANAGEMENT (A10),
 * ACCOUNT_DELETION (A12), ANNOUNCEMENTS (A13), CHALLENGES (A11, v1.0 preset
 * only — full management is v1.5, but the resource exists now so a TAB
 * role_name can target it later without a migration). SUSU_GROUPS added
 * v0.5-034 — no dedicated wireframe reference; a TAB scoped to susu-group
 * review shouldn't also get user-management or dispute access, and
 * vice versa, so this needs its own resource rather than reusing an
 * existing one.
 */
public enum AdminResource {
    USER_MANAGEMENT,
    KYC,
    DISPUTES,
    AUDIT_LOG,
    STAFF_MANAGEMENT,
    ACCOUNT_DELETION,
    ANNOUNCEMENTS,
    CHALLENGES,
    SUSU_GROUPS
}
