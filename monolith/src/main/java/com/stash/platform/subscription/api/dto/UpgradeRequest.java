package com.stash.platform.subscription.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record UpgradeRequest(
        @NotBlank @JsonProperty("paystack_subscription_token") String paystackSubscriptionToken
) {}
