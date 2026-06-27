package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VaultDepositResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("authorisation_url")     String authorisationUrl,
        @JsonProperty("paystack_reference")    String paystackReference,
        String                                         status
) {}
