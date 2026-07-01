package com.stash.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminLoginResponse(
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("account_type")  String accountType,
        @JsonProperty("expires_in")    int    expiresInSeconds
) {}
