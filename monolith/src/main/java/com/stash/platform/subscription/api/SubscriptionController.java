package com.stash.platform.subscription.api;

import com.stash.platform.subscription.api.dto.*;
import com.stash.platform.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public SubscriptionStatusResponse getStatus(@AuthenticationPrincipal UUID userId) {
        return subscriptionService.getStatus(userId);
    }

    @PostMapping("/upgrade/initiate")
    public UpgradeInitiateResponse initiateUpgrade(@AuthenticationPrincipal UUID userId) {
        return subscriptionService.initiateUpgrade(userId);
    }

    @PostMapping("/upgrade")
    @ResponseStatus(HttpStatus.CREATED)
    public UpgradeResponse upgrade(@Valid @RequestBody UpgradeRequest request,
                                    @AuthenticationPrincipal UUID userId) {
        return subscriptionService.upgrade(userId, request.paystackSubscriptionToken());
    }

    @PostMapping("/downgrade")
    public DowngradePreviewResponse downgrade(@RequestBody DowngradeRequest request,
                                               @AuthenticationPrincipal UUID userId) {
        return request.confirm()
                ? subscriptionService.commitDowngrade(userId)
                : subscriptionService.previewDowngrade(userId);
    }
}
