package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Internal request from the monolith to initiate a deposit.
 *
 * <p>The monolith has already validated:
 * <ul>
 *   <li>JWT is valid and user is authenticated.</li>
 *   <li>The vault belongs to the authenticated user.</li>
 *   <li>The vault is ACTIVE.</li>
 * </ul>
 *
 * <p>The Payments Service validates:
 * <ul>
 *   <li>{@code ledgerAccountId} exists in ledger.ledger_accounts.</li>
 *   <li>The account is ACTIVE.</li>
 *   <li>{@code amount} is positive.</li>
 * </ul>
 */
public record DepositInitiateRequest(

        @NotNull(message = "ledgerAccountId is required")
        @JsonProperty("ledger_account_id")
        UUID ledgerAccountId,

        @NotNull(message = "userId is required")
        @JsonProperty("user_id")
        UUID userId,

        @Min(value = 1, message = "amount must be at least 1 pesewa")
        long amount,

        @NotBlank(message = "payment_method is required")
        @JsonProperty("payment_method")
        String paymentMethod,       // MOMO or CARD

        @NotBlank(message = "customer_email is required")
        @JsonProperty("customer_email")
        String customerEmail,

        // MoMo-specific fields; null for CARD
        @JsonProperty("mobile_number")
        String mobileNumber,

        @JsonProperty("mobile_provider")
        String mobileProvider,      // mtn, vodafone, airteltigo

        @NotBlank(message = "correlation_id is required")
        @JsonProperty("correlation_id")
        String correlationId,

        // Business context — stored on ledger_transactions
        @JsonProperty("business_reference_id")
        UUID businessReferenceId,

        @JsonProperty("business_reference_type")
        String businessReferenceType    // e.g. VAULT_DEPOSIT
) {}
