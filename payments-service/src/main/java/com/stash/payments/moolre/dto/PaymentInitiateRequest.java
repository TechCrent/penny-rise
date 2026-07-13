package com.stash.payments.moolre.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentInitiateRequest(
        int type,
        String channel,
        String currency,
        String payer,
        String amount,
        @JsonProperty("externalref") String externalRef,
        @JsonProperty("otpcode") String otpCode,
        String reference,
        @JsonProperty("sessionid") String sessionId,
        @JsonProperty("accountnumber") String accountNumber
) {
    public static PaymentInitiateRequest of(
            String channel,
            String payerInternational,
            String amountGhs,
            String externalRef,
            String reference,
            String accountNumber) {
        return new PaymentInitiateRequest(
                1, channel, "GHS", payerInternational, amountGhs,
                externalRef, null, reference, null, accountNumber);
    }

    public static PaymentInitiateRequest withOtp(
            String channel,
            String payerInternational,
            String amountGhs,
            String externalRef,
            String reference,
            String otpCode,
            String accountNumber) {
        return new PaymentInitiateRequest(
                1, channel, "GHS", payerInternational, amountGhs,
                externalRef, otpCode, reference, null, accountNumber);
    }
}
