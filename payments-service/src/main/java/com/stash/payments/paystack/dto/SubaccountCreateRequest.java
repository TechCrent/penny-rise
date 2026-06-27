package com.stash.payments.paystack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SubaccountCreateRequest(
        @JsonProperty("business_name") String businessName,
        @JsonProperty("settlement_bank") String settlementBank,
        @JsonProperty("account_number") String accountNumber,
        @JsonProperty("percentage_charge") double percentageCharge,
        String description
) {}
