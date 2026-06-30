package com.stash.platform.susu.api;

import com.stash.platform.susu.api.dto.SusuGroupDetailResponse;
import com.stash.platform.susu.api.dto.SusuGroupListItemResponse;
import com.stash.platform.susu.service.SusuGroupQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/susu/groups")
public class SusuGroupQueryController {

    private final SusuGroupQueryService queryService;

    public SusuGroupQueryController(SusuGroupQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<SusuGroupListItemResponse> listGroups(
            @AuthenticationPrincipal UUID callerId,
            @RequestParam(name = "include_inactive", defaultValue = "false")
                    boolean includeInactive,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {
        return queryService.listGroups(
                callerId, includeInactive,
                correlationId != null ? correlationId : "susu-list-" + UUID.randomUUID());
    }

    @GetMapping("/{groupId}")
    public SusuGroupDetailResponse getGroupDetail(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UUID callerId,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {
        return queryService.getGroupDetail(
                groupId, callerId,
                correlationId != null ? correlationId : "susu-detail-" + UUID.randomUUID());
    }
}
