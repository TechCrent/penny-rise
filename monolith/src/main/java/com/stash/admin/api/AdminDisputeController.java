package com.stash.admin.api;

import com.stash.admin.api.dto.AdminDisputeListResponse;
import com.stash.admin.api.dto.BulkActionResultResponse;
import com.stash.admin.api.dto.BulkResolveDisputesRequest;
import com.stash.admin.api.dto.CloseDisputeRequest;
import com.stash.admin.api.dto.ResolveDisputeRequest;
import com.stash.admin.service.AdminDisputeService;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).DISPUTES)")
    @GetMapping
    public AdminDisputeListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return disputeService.listQueue(status, PageRequest.of(page, size));
    }

    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).DISPUTES)")
    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assign(@PathVariable UUID id, Authentication authentication) {
        disputeService.assign(id, adminIdOf(authentication));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).DISPUTES)")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID id,
                                         @Valid @RequestBody ResolveDisputeRequest request,
                                         Authentication authentication) {
        disputeService.resolve(id, adminIdOf(authentication), request.resolution());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).DISPUTES)")
    @PostMapping("/bulk-resolve")
    public BulkActionResultResponse bulkResolve(@Valid @RequestBody BulkResolveDisputesRequest request,
                                                 Authentication authentication) {
        var results = disputeService.bulkResolve(request.ids(), adminIdOf(authentication), request.resolution());
        return new BulkActionResultResponse(results);
    }

    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).DISPUTES)")
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
