package com.stash.admin.api;

import com.stash.admin.api.dto.SuspendUserRequest;
import com.stash.admin.rbac.AdminResource;
import com.stash.admin.rbac.RequiresAdminResource;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import com.stash.admin.service.AdminUserMutationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users/{id}")
public class AdminUserMutationController {

    private final AdminUserMutationService mutationService;

    public AdminUserMutationController(AdminUserMutationService mutationService) {
        this.mutationService = mutationService;
    }

    @PostMapping("/suspend")
    @RequiresAdminResource(AdminResource.USER_MANAGEMENT)
    public ResponseEntity<Void> suspend(@PathVariable UUID id,
                                         @Valid @RequestBody SuspendUserRequest request,
                                         Authentication authentication) {
        mutationService.suspend(id, adminIdOf(authentication), request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/restore")
    @RequiresAdminResource(AdminResource.USER_MANAGEMENT)
    public ResponseEntity<Void> restore(@PathVariable UUID id, Authentication authentication) {
        mutationService.restore(id, adminIdOf(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/force-logout")
    @RequiresAdminResource(AdminResource.USER_MANAGEMENT)
    public ResponseEntity<Void> forceLogout(@PathVariable UUID id, Authentication authentication) {
        mutationService.forceLogout(id, adminIdOf(authentication));
        return ResponseEntity.noContent().build();
    }

    private UUID adminIdOf(Authentication authentication) {
        return ((AdminTokenClaims) authentication.getDetails()).adminAccountId();
    }
}
