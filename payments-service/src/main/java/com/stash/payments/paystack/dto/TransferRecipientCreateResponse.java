package com.stash.payments.paystack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferRecipientCreateResponse(
        boolean status,
        String message,
        RecipientData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecipientData(
            @JsonProperty("recipient_code") String recipientCode,
            String name,
            @JsonProperty("account_number") String accountNumber
    ) {}
}
