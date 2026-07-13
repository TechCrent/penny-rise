package com.stash.payments.moolre.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidateRecipientRequest(
        int type,
        String receiver,
        String channel,
        @JsonProperty("sublistid") String sublistId,
        String currency,
        @JsonProperty("accountnumber") String accountNumber
) {
    public static ValidateRecipientRequest of(
            String channel, String receiverInternational, String accountNumber) {
        return new ValidateRecipientRequest(
                1, receiverInternational, channel, null, "GHS", accountNumber);
    }
}
