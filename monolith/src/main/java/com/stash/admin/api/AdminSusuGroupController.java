package com.stash.admin.api;

import com.stash.admin.api.dto.AdminSusuGroupDetailResponse;
import com.stash.admin.api.dto.FlaggedSusuGroupListResponse;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import com.stash.admin.service.AdminSusuGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/susu-groups")
public class AdminSusuGroupController {

    private final AdminSusuGroupService service;

    public AdminSusuGroupController(AdminSusuGroupService service) {
        this.service = service;
    }

    /**
     * {@code ?flagged=true} returns the flagged-for-review queue (with
     * shortfall detail); omitted or {@code false} returns general
     * browsing across all susu groups — gap-analysis fix: previously
     * unflagged listing 400'd outright, so the only reachable groups from
     * the admin console were ones that had already been auto-flagged.
     */
    @GetMapping
    @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).SUSU_GROUPS)")
    public FlaggedSusuGroupListResponse listFlagged(
            @RequestParam(required = false, defaultValue = "false") boolean flagged,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return flagged ? service.listFlagged(page, size) : service.listAll(page, size);
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
