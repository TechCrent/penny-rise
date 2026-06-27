package com.stash.payments.paystack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransferRecipientCreateRequest(
        String type,                                                    // "mobile_money"
        String name,                                                    // display name
        @JsonProperty("account_number") String accountNumber,           // MoMo number
        @JsonProperty("bank_code")      String bankCode,                // MTN = "MTN", Vodafone = "VDF", AirtelTigo = "ATL"
        String currency                                                  // "GHS"
) {}
