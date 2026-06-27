package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record EarlyExitRequest(
        @NotBlank(message = "reason is required")
        String reason,       // SCHOOL_FEES_EMERGENCY, MEDICAL, FAMILY, OTHER

        @NotBlank(message = "destination_momo_number is required")
        @JsonProperty("destination_momo_number")
        String destinationMomoNumber,

        @NotBlank(message = "momo_provider is required")
        @JsonProperty("momo_provider")
        String momoProvider     // mtn, vodafone, airteltigo
) {}
