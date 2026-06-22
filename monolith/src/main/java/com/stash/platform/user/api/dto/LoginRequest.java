package com.stash.platform.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password,

        /** Client-generated stable device identifier. Defaults to "unknown" if absent. */
        String deviceId,

        /** Human-readable device label, e.g. "iPhone 15 Pro". */
        String deviceLabel
) {}