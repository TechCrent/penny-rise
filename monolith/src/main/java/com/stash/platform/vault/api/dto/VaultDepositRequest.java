package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record VaultDepositRequest(

        @Min(value = 1, message = "amount must be at least 1 pesewa")
        long amount,

        @NotBlank(message = "payment_method is required")
        @JsonProperty("payment_method")
        String paymentMethod,

        @JsonProperty("mobile_number")
        String mobileNumber,

        @JsonProperty("mobile_provider")
        String mobileProvider
) {}
