package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Response for a successful POST /api/v1/auth/login.
 *
 * <p>No password hash, no sensitive fields.
 */
public record LoginResponse(
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in")    long expiresIn,    // seconds
        @JsonProperty("user")          UserProfile user
) {
    public record UserProfile(
            UUID id,
            String email,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("kyc_status")   String kycStatus
    ) {}
}