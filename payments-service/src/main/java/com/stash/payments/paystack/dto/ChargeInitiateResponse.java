package com.stash.payments.paystack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChargeInitiateResponse(
        boolean status,
        String message,
        ChargeData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChargeData(
            String reference,
            @JsonProperty("display_text") String displayText,
            String status
    ) {}
}
