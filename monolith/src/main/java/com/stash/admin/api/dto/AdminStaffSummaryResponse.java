package com.stash.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Staff directory row. Never includes password_hash.
 */
public record AdminStaffSummaryResponse(
        UUID id,
        String email,
        @JsonProperty("full_name")      String fullName,
        @JsonProperty("account_type")   String accountType,
        @JsonProperty("role_name")      String roleName,
        @JsonProperty("is_active")      boolean isActive,
        @JsonProperty("deactivated_at") Instant deactivatedAt,
        @JsonProperty("created_at")     Instant createdAt
) {}
