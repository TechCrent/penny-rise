package com.stash.kyc.submission.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for POST /api/v1/kyc/submissions.
 *
 * <p>Ghana Card format: GHA-XXXXXXXXX-X (9 digits, a hyphen, then 1 check
 * digit) per NIA's published format. Validated here at the Bean Validation
 * layer; the regex intentionally does not validate the check digit
 * algorithm — that level of validation belongs to the provider integration
 * (v0.2-024), not request parsing.
 */
public record CreateSubmissionRequest(

        @NotBlank(message = "Ghana Card number is required")
        @Pattern(
            regexp = "^GHA-[0-9]{9}-[0-9]$",
            message = "Ghana Card number must be in the format GHA-XXXXXXXXX-X"
        )
        @JsonProperty("ghana_card_number")
        String ghanaCardNumber,

        @NotBlank(message = "Full name is required")
        @JsonProperty("full_name")
        String fullName
) {}
