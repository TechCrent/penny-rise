package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/users/me/change-password.
 *
 * <p>For an already-logged-in user — distinct from the token-based
 * forgot/reset-password flow, which doesn't require knowing the current
 * password. {@code newPassword} follows the same complexity rule as
 * signup ({@link SignupRequest}).
 */
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        @JsonProperty("current_password")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must be at least 8 characters")
        @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$",
            message = "New password must contain at least one number and one special character"
        )
        @JsonProperty("new_password")
        String newPassword
) {}
