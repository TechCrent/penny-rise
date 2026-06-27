package com.stash.payments.paystack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionVerifyResponse(
        boolean status,
        String message,
        TransactionData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransactionData(
            String reference,
            String status,               // "success", "failed", "abandoned"
            long amount,
            @JsonProperty("gateway_response") String gatewayResponse,
            @JsonProperty("paid_at") String paidAt
    ) {}
}
