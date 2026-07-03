package com.stash.platform.subscription.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpgradeInitiateResponse(
        @JsonProperty("authorization_url") String authorizationUrl,
        String reference
) {}
