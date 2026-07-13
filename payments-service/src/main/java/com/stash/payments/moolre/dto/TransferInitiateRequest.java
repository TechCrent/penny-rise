package com.stash.payments.moolre.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransferInitiateRequest(
        int type,
        String channel,
        String currency,
        String amount,
        String receiver,
        @JsonProperty("sublistid") String sublistId,
        @JsonProperty("externalref") String externalRef,
        String reference,
        @JsonProperty("accountnumber") String accountNumber
) {
    public static TransferInitiateRequest of(
            String channel,
            String receiverInternational,
            String amountGhs,
            String externalRef,
            String reference,
            String accountNumber) {
        return new TransferInitiateRequest(
                1, channel, "GHS", amountGhs, receiverInternational,
                null, externalRef, reference, accountNumber);
    }
}
