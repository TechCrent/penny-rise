package com.stash.admin.api;

import com.stash.admin.api.dto.CreateAdminAccountRequest;
import com.stash.admin.service.AdminAccountCreationService;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Staff management endpoints (A10). Two RBAC patterns are deliberately used
 * here — see AdminRoleMapping's "FLAGGED CONFLICT #1" javadoc:
 *  - listStaff: a plain resource gate (STAFF_MANAGEMENT) — SUPER only.
 *    Uses explicit @PreAuthorize because the project runs Spring Boot 3.3
 *    (Spring Security 6.3) which does not support the {value} meta-annotation
 *    substitution needed by @RequiresAdminResource. Switch to
 *    @RequiresAdminResource(AdminResource.STAFF_MANAGEMENT) when the project
 *    upgrades to Spring Boot 3.4+.
 *  - createAdmin: NOT gated by STAFF_MANAGEMENT (that would block VICE_SUPER
 *    entirely). Gated directly against the creation hierarchy instead, via
 *    AdminAccessEvaluator.canCreate reading the already-bound @RequestBody.
 *
 * Minimal stub to exercise the RBAC framework for v0.5-004 — full
 * list/detail/deactivate endpoints belong to whichever issue owns A10 fully.
 */
@RestController
@RequestMapping("/api/v1/admin/staff")
public class AdminStaffController {

    private final AdminAccountCreationService creationService;

    public AdminStaffController(AdminAccountCreationService creationService) {
        this.creationService = creationService;
    }

    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).STAFF_MANAGEMENT)")
    @GetMapping
    public Object listStaff(Authentication authentication) {
        throw new UnsupportedOperationException("staff list — implemented in A10's owning issue");
    }

    @PreAuthorize("@adminAccessEvaluator.canCreate(#root, #request.accountType())")
    @PostMapping
    public Object createAdmin(@Valid @RequestBody CreateAdminAccountRequest request,
                               Authentication authentication) {
        AdminTokenClaims creator = (AdminTokenClaims) authentication.getDetails();
        creationService.assertCanCreate(creator, request.accountType());
        throw new UnsupportedOperationException("creation persistence — implemented in A10's owning issue");
    }
}
