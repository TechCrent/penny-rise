package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record VaultDepositResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("authorisation_url")     String authorisationUrl,
        @JsonProperty("provider_reference")
        @JsonAlias("paystack_reference")
        String providerReference,
        String status,
        @JsonProperty("otp_required") boolean otpRequired
) {}
