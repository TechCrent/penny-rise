package com.stash.platform.susu.api;

import com.stash.platform.susu.service.SusuGroupCancellationService;
import com.stash.platform.susu.service.SusuOrganiserMemberRemovalService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/susu/groups")
public class SusuOrganiserActionsController {

    private final SusuOrganiserMemberRemovalService removalService;
    private final SusuGroupCancellationService      cancellationService;

    public SusuOrganiserActionsController(
            SusuOrganiserMemberRemovalService removalService,
            SusuGroupCancellationService cancellationService) {
        this.removalService      = removalService;
        this.cancellationService = cancellationService;
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal UUID callerId,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        removalService.removeMember(
                groupId, memberId, callerId,
                correlationId != null ? correlationId : "susu-remove-" + UUID.randomUUID());
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelGroup(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UUID callerId,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        cancellationService.cancelGroup(
                groupId, callerId,
                correlationId != null ? correlationId : "susu-cancel-" + UUID.randomUUID());
    }
}
