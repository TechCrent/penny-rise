package com.stash.platform.susu.api;

import com.stash.platform.susu.service.SusuMemberLeaveService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/susu/groups")
public class SusuMemberLeaveController {

    private final SusuMemberLeaveService leaveService;

    public SusuMemberLeaveController(SusuMemberLeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping("/{groupId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveGroup(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UUID callerId,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        leaveService.leaveGroup(
                groupId, callerId,
                correlationId != null ? correlationId : "susu-leave-" + UUID.randomUUID());
    }
}
