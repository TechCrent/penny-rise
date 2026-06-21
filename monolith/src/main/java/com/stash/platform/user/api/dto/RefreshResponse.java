package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for a successful POST /api/v1/auth/refresh.
 *
 * <p>Contains new tokens only. No user profile returned — the
 * access token claims already carry kyc_status, account_status, etc.
 */
public record RefreshResponse(
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in")    long expiresIn      // seconds (always 900)
) {}