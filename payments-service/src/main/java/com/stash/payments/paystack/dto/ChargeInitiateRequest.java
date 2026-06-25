package com.stash.payments.paystack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChargeInitiateRequest(
        String email,
        long amount,                            // pesewas
        @JsonProperty("mobile_money") MobileMoneyChannel mobileMoneyChannel,
        String currency,                        // "GHS"
        @JsonProperty("subaccount") String subaccountCode
) {
    public record MobileMoneyChannel(String phone, String provider) {}
}
