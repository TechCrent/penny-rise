package com.stash.admin.rbac;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for admin RBAC (Issue v0.5-004 AC: "Role mappings
 * documented in a single centralised configuration file — no scattered
 * per-endpoint logic"). Every {@link RequiresAdminResource} check and every
 * account-creation check routes through this class.
 *
 * Rules encoded (System Design §7.3, Issue v0.5-004 AC):
 *  - SUPER: every AdminResource.
 *  - VICE_SUPER: every AdminResource except STAFF_MANAGEMENT.
 *  - TAB: exactly the resource whose enum name equals their role_name claim.
 *
 * FLAGGED CONFLICT #1 (Stash_Wireframes.docx A10 vs. Issue Plan v5):
 * A10 says the staff directory is "visible only to SUPER admins." Issue Plan
 * v5 and this issue's AC say VICE_SUPER may create TAB accounts. Resolved by
 * splitting "view the staff directory" (STAFF_MANAGEMENT resource, SUPER
 * only) from "create a subordinate admin" (canCreate(), SUPER + VICE_SUPER).
 * VICE_SUPER is never granted STAFF_MANAGEMENT itself — no directory view,
 * no deactivate/change-type rights — only the narrower creation right.
 * Confirm with product before A10 ships.
 *
 * FLAGGED CONFLICT #2 (Schema doc §2.1 vs. Issue v0.5-004 AC):
 * Schema doc documents role_name as a free-text display label ("KYC
 * Reviewer"). This issue's AC requires role_name to drive enforcement
 * directly (examples given: KYC, DISPUTES, USER_MANAGEMENT). Implemented as
 * a controlled value equal to an AdminResource name. If product still wants
 * a free-text label for A10, that's a second column, not a reuse of this one.
 *
 * INTERPRETATION (Issue text, literal reading vs. AC "full access"):
 * The issue text reads literally as a hop-restricted chain (SUPER creates
 * VICE_SUPER; VICE_SUPER creates TAB — no direct SUPER->TAB). Since the AC
 * also says "SUPER: full access to all admin endpoints," SUPER's creation
 * rights are implemented as a superset (VICE_SUPER *and* TAB), not a
 * restricted hop. Flagging in case the literal chain was intended.
 */
@Component
public class AdminRoleMapping {

    private static final Set<AdminResource> ALL_RESOURCES = EnumSet.allOf(AdminResource.class);

    private static final Set<AdminResource> VICE_SUPER_RESOURCES =
            EnumSet.complementOf(EnumSet.of(AdminResource.STAFF_MANAGEMENT));

    private static final Map<AdminAccountType, Set<AdminResource>> NON_TAB_ACCESS = Map.of(
            AdminAccountType.SUPER, ALL_RESOURCES,
            AdminAccountType.VICE_SUPER, VICE_SUPER_RESOURCES
    );

    private static final Map<AdminAccountType, Set<AdminAccountType>> CREATION_RIGHTS = Map.of(
            AdminAccountType.SUPER, EnumSet.of(AdminAccountType.VICE_SUPER, AdminAccountType.TAB),
            AdminAccountType.VICE_SUPER, EnumSet.of(AdminAccountType.TAB),
            AdminAccountType.TAB, EnumSet.noneOf(AdminAccountType.class)
    );

    public boolean hasAccess(AdminAccountType accountType, String roleName, AdminResource resource) {
        if (accountType == AdminAccountType.TAB) {
            return roleName != null && resource.name().equals(roleName);
        }
        Set<AdminResource> granted = NON_TAB_ACCESS.get(accountType);
        return granted != null && granted.contains(resource);
    }

    public boolean canCreate(AdminAccountType creatorType, AdminAccountType targetType) {
        Set<AdminAccountType> creatable = CREATION_RIGHTS.get(creatorType);
        return creatable != null && creatable.contains(targetType);
    }
}
