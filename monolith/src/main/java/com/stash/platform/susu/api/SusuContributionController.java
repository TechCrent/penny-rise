package com.stash.platform.susu.api;

import com.stash.platform.susu.api.dto.SusuContributionResponse;
import com.stash.platform.susu.service.SusuContributionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/susu/contributions")
public class SusuContributionController {

    private final SusuContributionService contributionService;

    public SusuContributionController(SusuContributionService contributionService) {
        this.contributionService = contributionService;
    }

    @PostMapping("/{roundId}")
    @ResponseStatus(HttpStatus.OK)
    public SusuContributionResponse payContribution(
            @PathVariable UUID roundId,
            @AuthenticationPrincipal UUID callerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false)
                    String correlationId) {

        return contributionService.payContribution(
                roundId, callerId,
                correlationId != null ? correlationId : "susu-contrib-" + UUID.randomUUID(),
                idempotencyKey);
    }
}
