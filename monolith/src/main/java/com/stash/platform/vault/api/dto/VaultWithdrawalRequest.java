package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record VaultWithdrawalRequest(

        @Min(value = 1, message = "amount must be at least 1 pesewa")
        long amount,

        @NotBlank(message = "destination_momo_number is required")
        @JsonProperty("destination_momo_number")
        String destinationMomoNumber,

        @NotBlank(message = "momo_provider is required")
        @JsonProperty("momo_provider")
        String momoProvider     // mtn, vodafone, airteltigo
) {}
