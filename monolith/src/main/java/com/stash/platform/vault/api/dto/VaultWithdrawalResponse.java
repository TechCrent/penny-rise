package com.stash.platform.vault.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VaultWithdrawalResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("paystack_transfer_code") String paystackTransferCode,
        String                                          status
) {}
