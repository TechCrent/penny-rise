package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record TransferResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        @JsonProperty("ledger_transaction_id") UUID ledgerTransactionId,
        String status
) {
    public static TransferResponse posted(String ref, UUID ledgerTxnId) {
        return new TransferResponse(ref, ledgerTxnId, "POSTED");
    }
}
