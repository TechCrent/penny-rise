package com.stash.platform.susu.api;

import com.stash.platform.susu.api.dto.CreateSusuGroupRequest;
import com.stash.platform.susu.api.dto.SusuGroupResponse;
import com.stash.platform.susu.service.SusuGroupCreationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/susu/groups")
public class SusuGroupCreationController {

    private final SusuGroupCreationService creationService;

    public SusuGroupCreationController(SusuGroupCreationService creationService) {
        this.creationService = creationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SusuGroupResponse createGroup(
            @Valid @RequestBody CreateSusuGroupRequest request,
            @AuthenticationPrincipal UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        return creationService.createGroup(
                userId, request,
                correlationId != null ? correlationId : "susu-create-" + UUID.randomUUID(),
                idempotencyKey);
    }
}
