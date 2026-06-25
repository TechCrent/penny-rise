package com.stash.payments.paystack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferInitiateResponse(
        boolean status,
        String message,
        TransferData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransferData(
            String reference,
            String status,
            @JsonProperty("transfer_code") String transferCode
    ) {}
}
