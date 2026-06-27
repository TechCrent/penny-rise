package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WithdrawalInitiateResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("paystack_transfer_code") String paystackTransferCode,
        String status
) {
    public static WithdrawalInitiateResponse pending(String ref, String transferCode) {
        return new WithdrawalInitiateResponse(ref, transferCode, "PENDING");
    }
}
