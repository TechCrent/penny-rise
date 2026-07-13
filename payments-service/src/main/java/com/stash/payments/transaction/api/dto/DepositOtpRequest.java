package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Completes a MoMo deposit that returned Moolre {@code TP14} (OTP required).
 */
public record DepositOtpRequest(
        @NotNull
        @JsonProperty("user_id")
        UUID userId,

        @NotBlank
        @JsonProperty("otp_code")
        String otpCode,

        @NotBlank
        @JsonProperty("mobile_number")
        String mobileNumber,

        @NotBlank
        @JsonProperty("mobile_provider")
        String mobileProvider,

        @JsonProperty("correlation_id")
        String correlationId
) {}
