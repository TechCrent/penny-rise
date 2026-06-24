package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Response body for a successful POST /api/v1/auth/signup.
 */
public record SignupResponse(
        UUID id,
        String email,
        @JsonProperty("display_name") String displayName,
        String message
) {}