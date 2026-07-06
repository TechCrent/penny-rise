package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record GdprVaultSummary(
        UUID id,
        String name,
        @JsonProperty("vault_type") String vaultType,
        String status,
        @JsonProperty("created_at") Instant createdAt
) {}
