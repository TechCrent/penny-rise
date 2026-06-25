package com.stash.payments.paystack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SubaccountCreateResponse(
        boolean status,
        String message,
        SubaccountData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubaccountData(
            @JsonProperty("subaccount_code") String subaccountCode,
            @JsonProperty("business_name") String businessName,
            @JsonProperty("settlement_bank") String settlementBank,
            @JsonProperty("account_number") String accountNumber
    ) {}
}
