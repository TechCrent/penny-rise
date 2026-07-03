package com.stash.platform.subscription.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record SubscriptionStatusResponse(
        String tier,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("ends_at") Instant endsAt,
        String source,
        @JsonProperty("free_transfers_remaining") int freeTransfersRemaining
) {}
