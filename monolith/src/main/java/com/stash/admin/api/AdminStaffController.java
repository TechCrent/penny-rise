package com.stash.admin.api;

import com.stash.admin.api.dto.AdminStaffListResponse;
import com.stash.admin.api.dto.AdminStaffSummaryResponse;
import com.stash.admin.api.dto.CreateAdminAccountRequest;
import com.stash.admin.service.AdminAccountCreationService;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Staff management endpoints (A10). Two RBAC patterns are deliberately used
 * here — see AdminRoleMapping's "FLAGGED CONFLICT #1" javadoc:
 *  - listStaff/deactivate: a plain resource gate (STAFF_MANAGEMENT) — SUPER
 *    only. Uses explicit @PreAuthorize because the project runs Spring Boot
 *    3.3 (Spring Security 6.3) which does not support the {value}
 *    meta-annotation substitution needed by @RequiresAdminResource. Switch
 *    to @RequiresAdminResource(AdminResource.STAFF_MANAGEMENT) when the
 *    project upgrades to Spring Boot 3.4+.
 *  - createAdmin: NOT gated by STAFF_MANAGEMENT (that would block VICE_SUPER
 *    entirely). Gated directly against the creation hierarchy instead, via
 *    AdminAccessEvaluator.canCreate reading the already-bound @RequestBody.
 *
 * <p>Gap-analysis fix: list/create/deactivate were previously stubs that
 * threw {@code UnsupportedOperationException} despite the RBAC gates being
 * fully wired — there was no working invite/deactivate UI for admin
 * operators anywhere. All three now delegate to
 * {@link AdminAccountCreationService}, which persists via
 * {@code AdminAccountRepository}.
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
    public AdminStaffListResponse listStaff(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AdminStaffSummaryResponse> result = creationService.list(PageRequest.of(page, size));
        return new AdminStaffListResponse(
                result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    @PreAuthorize("@adminAccessEvaluator.canCreate(#root, #request.accountType())")
    @PostMapping
    public ResponseEntity<AdminStaffSummaryResponse> createAdmin(
            @Valid @RequestBody CreateAdminAccountRequest request,
            Authentication authentication) {
        AdminTokenClaims creator = (AdminTokenClaims) authentication.getDetails();
        AdminStaffSummaryResponse created = creationService.create(creator, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).STAFF_MANAGEMENT)")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        creationService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
