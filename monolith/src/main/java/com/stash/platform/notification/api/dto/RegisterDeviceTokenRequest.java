package com.stash.platform.notification.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterDeviceTokenRequest(
        @NotBlank String token,
        @NotBlank String platform
) {}
