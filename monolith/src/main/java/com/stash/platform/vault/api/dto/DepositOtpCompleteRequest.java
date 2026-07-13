package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * User-facing OTP completion for a PENDING MoMo deposit (Moolre TP14).
 */
public record DepositOtpCompleteRequest(
        @NotBlank
        @JsonProperty("otp_code")
        String otpCode,

        @NotBlank
        @JsonProperty("mobile_number")
        String mobileNumber,

        @NotBlank
        @JsonProperty("mobile_provider")
        String mobileProvider
) {}
