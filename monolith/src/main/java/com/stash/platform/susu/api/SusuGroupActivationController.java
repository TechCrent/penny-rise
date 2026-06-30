package com.stash.platform.susu.api;

import com.stash.platform.susu.api.dto.SusuActivationResponse;
import com.stash.platform.susu.service.SusuGroupActivationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/susu/groups")
public class SusuGroupActivationController {

    private final SusuGroupActivationService activationService;

    public SusuGroupActivationController(SusuGroupActivationService activationService) {
        this.activationService = activationService;
    }

    @PostMapping("/{groupId}/activate")
    @ResponseStatus(HttpStatus.OK)
    public SusuActivationResponse activate(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UUID callerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        return activationService.activate(
                groupId, callerId,
                correlationId != null ? correlationId : "susu-activate-" + UUID.randomUUID());
    }
}
