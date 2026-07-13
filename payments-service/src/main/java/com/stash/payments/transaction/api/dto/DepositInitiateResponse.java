package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DepositInitiateResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("authorisation_url")     String authorisationUrl,
        @JsonProperty("provider_reference")
        @JsonAlias("paystack_reference")
        String providerReference,
        String status,
        @JsonProperty("otp_required") boolean otpRequired
) {
    public static DepositInitiateResponse pending(String txnRef,
                                                   String authUrl,
                                                   String providerRef) {
        return new DepositInitiateResponse(txnRef, authUrl, providerRef, "PENDING", false);
    }

    public static DepositInitiateResponse otpRequired(String txnRef, String providerRef) {
        return new DepositInitiateResponse(
                txnRef,
                "Enter the verification code sent by SMS",
                providerRef,
                "PENDING",
                true);
    }
}
