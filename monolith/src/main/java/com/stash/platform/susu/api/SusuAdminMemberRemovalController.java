package com.stash.platform.susu.api;

import com.stash.platform.susu.service.SusuMemberRemovalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal endpoint for admin/dispute-path member removal.
 * Protected by the InternalServiceAuthFilter — not accessible to end users.
 */
@RestController
@RequestMapping("/internal/v1/susu/groups")
public class SusuAdminMemberRemovalController {

    private final SusuMemberRemovalService removalService;

    public SusuAdminMemberRemovalController(SusuMemberRemovalService removalService) {
        this.removalService = removalService;
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @RequestParam(value = "admin_id", required = false) UUID adminUserId,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        removalService.removeMember(
                groupId, userId,
                adminUserId != null ? adminUserId : UUID.randomUUID(),
                correlationId != null ? correlationId : "susu-remove-" + UUID.randomUUID());
    }
}
