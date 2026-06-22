package com.stash.platform.user.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for PATCH /api/v1/users/me.
 *
 * <p>Only display_name and phone are accepted here, by design — this DTO
 * has no fields for kyc_status, account_status, or subscription_tier, so
 * there is nothing for a malicious caller to even attempt to set. Any
 * extra JSON fields submitted by the client are ignored by Jackson
 * (default behaviour — no @JsonIgnoreProperties needed since we don't
 * fail on unknown properties for this lenient PATCH endpoint).
 *
 * <p>Both fields are optional — a PATCH may update just one of them.
 * {@code null} means "no change"; to clear phone in a future version,
 * a separate explicit mechanism would be needed (not in scope for v0.2).
 */

public record UpdateUserProfileRequest(
        @Size(max = 100, message = "Display name must not exceed 100 characters")

        String displayName,

/**

         * Ghanaian mobile format: +233 followed by 9 digits, e.g. +233501234567.

         * Local format (0501234567) is also accepted and normalised server-side

         * — handled in the service layer, not here, since normalisation isn't

         * a validation concern.

         */

        @Pattern(

                regexp = "^(\\+233[0-9]{9}|0[0-9]{9})$",

                message = "Phone must be a valid Ghanaian mobile number (e.g. +233501234567 or 0501234567)"

        )

        String phone

) {}