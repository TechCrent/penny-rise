package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record WithdrawalInitiateResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("provider_transfer_code")
        @JsonAlias("paystack_transfer_code")
        String providerTransferCode,
        String status
) {
    public static WithdrawalInitiateResponse pending(String ref, String transferCode) {
        return new WithdrawalInitiateResponse(ref, transferCode, "PENDING");
    }

    public static WithdrawalInitiateResponse completed(String ref, String transferCode) {
        return new WithdrawalInitiateResponse(ref, transferCode, "COMPLETED");
    }
}
