package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "refresh_token is required")
        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("all_devices")
        boolean allDevices
) {
    public LogoutRequest {
        // Default allDevices to false if not supplied — handled by Jackson default for primitive boolean
    }
}
