package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/auth/reset-password.
 *
 * <p>Password strength rule is identical to {@link SignupRequest} —
 * minimum 8 characters, at least one number and one special character.
 */
public record ResetPasswordRequest(

        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$",
                message = "Password must contain at least one number and one special character"
        )
        @JsonProperty("new_password")
        String newPassword
) {}