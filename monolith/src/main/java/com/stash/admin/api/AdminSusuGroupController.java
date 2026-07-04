package com.stash.admin.api;

import com.stash.admin.api.dto.AdminSusuGroupDetailResponse;
import com.stash.admin.api.dto.FlaggedSusuGroupListResponse;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import com.stash.admin.service.AdminSusuGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/susu-groups")
public class AdminSusuGroupController {

    private final AdminSusuGroupService service;

    public AdminSusuGroupController(AdminSusuGroupService service) {
        this.service = service;
    }

    /**
     * Per this issue's AC, only ?flagged=true is specified — an unflagged
     * listing has no defined behaviour here, so it's required explicit
     * rather than silently defaulting to "all groups."
     */
    @GetMapping
    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).SUSU_GROUPS)")
    public FlaggedSusuGroupListResponse listFlagged(
            @RequestParam(required = false, defaultValue = "false") boolean flagged,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!flagged) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This endpoint currently only supports ?flagged=true — unflagged listing is out of this issue's scope.");
        }
        return service.listFlagged(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).SUSU_GROUPS)")
    public AdminSusuGroupDetailResponse getDetail(@PathVariable UUID id) {
        return service.getDetail(id);
    }

    @PostMapping("/{id}/clear-flag")
    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).SUSU_GROUPS)")
    public ResponseEntity<Void> clearFlag(@PathVariable UUID id, Authentication authentication) {
        service.clearFlag(id, adminIdOf(authentication));
        return ResponseEntity.noContent().build();
    }

    private UUID adminIdOf(Authentication authentication) {
        return ((AdminTokenClaims) authentication.getDetails()).adminAccountId();
    }
}
