package com.stash.platform.subscription.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UpgradeResponse(
        String tier,
        @JsonProperty("started_at") Instant startedAt
) {}
