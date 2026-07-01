package com.stash.admin.api;

import com.stash.admin.api.dto.AdminDisputeListResponse;
import com.stash.admin.api.dto.CloseDisputeRequest;
import com.stash.admin.api.dto.ResolveDisputeRequest;
import com.stash.admin.rbac.AdminResource;
import com.stash.admin.rbac.RequiresAdminResource;
import com.stash.admin.service.AdminDisputeService;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/disputes")
public class AdminDisputeController {

    private final AdminDisputeService disputeService;

    public AdminDisputeController(AdminDisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @RequiresAdminResource(AdminResource.DISPUTES)
    @GetMapping
    public AdminDisputeListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return disputeService.listQueue(status, PageRequest.of(page, size));
    }

    @RequiresAdminResource(AdminResource.DISPUTES)
    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assign(@PathVariable UUID id, Authentication authentication) {
        disputeService.assign(id, adminIdOf(authentication));
        return ResponseEntity.noContent().build();
    }

    @RequiresAdminResource(AdminResource.DISPUTES)
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID id,
                                         @Valid @RequestBody ResolveDisputeRequest request,
                                         Authentication authentication) {
        disputeService.resolve(id, adminIdOf(authentication), request.resolution());
        return ResponseEntity.ok().build();
    }

    @RequiresAdminResource(AdminResource.DISPUTES)
    @PostMapping("/{id}/close-no-action")
    public ResponseEntity<Void> closeNoAction(@PathVariable UUID id,
                                               @Valid @RequestBody CloseDisputeRequest request,
                                               Authentication authentication) {
        disputeService.closeNoAction(id, adminIdOf(authentication), request.reason());
        return ResponseEntity.ok().build();
    }

    private UUID adminIdOf(Authentication authentication) {
        return ((AdminTokenClaims) authentication.getDetails()).adminAccountId();
    }
}
