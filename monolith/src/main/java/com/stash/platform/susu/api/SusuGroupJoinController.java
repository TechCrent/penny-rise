package com.stash.platform.susu.api;

import com.stash.platform.susu.api.dto.JoinSusuGroupRequest;
import com.stash.platform.susu.api.dto.JoinSusuGroupResponse;
import com.stash.platform.susu.service.SusuGroupJoinService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/susu/groups")
public class SusuGroupJoinController {

    private final SusuGroupJoinService joinService;

    public SusuGroupJoinController(SusuGroupJoinService joinService) {
        this.joinService = joinService;
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinSusuGroupResponse join(
            @Valid @RequestBody JoinSusuGroupRequest request,
            @AuthenticationPrincipal UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        return joinService.joinGroup(
                userId, request,
                correlationId != null ? correlationId : "susu-join-" + UUID.randomUUID(),
                idempotencyKey);
    }
}
