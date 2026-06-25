package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DepositInitiateResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("authorisation_url")     String authorisationUrl,
        @JsonProperty("paystack_reference")    String paystackReference,
        String status
) {
    // Convenience factory for the happy path
    public static DepositInitiateResponse pending(String txnRef,
                                                   String authUrl,
                                                   String paystackRef) {
        return new DepositInitiateResponse(txnRef, authUrl, paystackRef, "PENDING");
    }
}
