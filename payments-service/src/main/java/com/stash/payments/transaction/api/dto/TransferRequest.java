package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request for an internal ledger transfer.
 *
 * <p>All fields are provided by the monolith. The Payments Service trusts
 * that the monolith has verified user ownership and business rules, but
 * independently validates account existence, status, and balance.
 */
public record TransferRequest(

        @NotNull(message = "source_account_id is required")
        @JsonProperty("source_account_id")
        UUID sourceAccountId,

        @NotNull(message = "destination_account_id is required")
        @JsonProperty("destination_account_id")
        UUID destinationAccountId,

        @Min(value = 1, message = "amount must be at least 1 pesewa")
        long amount,

        @NotBlank(message = "transaction_type is required")
        @JsonProperty("transaction_type")
        String transactionType,

        @JsonProperty("business_reference_id")
        UUID businessReferenceId,

        @JsonProperty("business_reference_type")
        String businessReferenceType,

        @NotBlank(message = "correlation_id is required")
        @JsonProperty("correlation_id")
        String correlationId,

        String narrative
) {}
