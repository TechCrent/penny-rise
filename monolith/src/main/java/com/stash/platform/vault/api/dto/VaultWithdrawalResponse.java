package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record VaultWithdrawalResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("provider_transfer_code")
        @JsonAlias("paystack_transfer_code")
        String providerTransferCode,
        String status
) {}
