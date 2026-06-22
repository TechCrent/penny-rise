package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "refresh_token is required")
        @JsonProperty("refresh_token")
        String refreshToken,

        /** Optional: carried forward to the new token row for session visibility. */
        @JsonProperty("device_id")
        String deviceId,

        @JsonProperty("device_label")
        String deviceLabel
) {}
