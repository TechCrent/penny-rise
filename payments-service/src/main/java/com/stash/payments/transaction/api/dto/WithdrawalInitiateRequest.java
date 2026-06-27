package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WithdrawalInitiateRequest(

        @NotNull(message = "ledgerAccountId is required")
        @JsonProperty("ledger_account_id")
        UUID ledgerAccountId,

        @NotNull(message = "userId is required")
        @JsonProperty("user_id")
        UUID userId,

        @Min(value = 1, message = "amount must be at least 1 pesewa")
        long amount,

        @NotBlank(message = "destination_momo_number is required")
        @JsonProperty("destination_momo_number")
        String destinationMomoNumber,

        @NotBlank(message = "momo_provider is required")
        @JsonProperty("momo_provider")
        String momoProvider,

        @NotBlank(message = "recipient_name is required")
        @JsonProperty("recipient_name")
        String recipientName,

        @NotBlank(message = "customer_email is required")
        @JsonProperty("customer_email")
        String customerEmail,

        @NotBlank(message = "correlation_id is required")
        @JsonProperty("correlation_id")
        String correlationId,

        @JsonProperty("business_reference_id")
        UUID businessReferenceId,

        @JsonProperty("business_reference_type")
        String businessReferenceType
) {}
