package com.stash.payments.ledger.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record StatementEntryDto(
        @JsonProperty("entry_id")                UUID    entryId,
        String                                           direction,
        @JsonProperty("amount_pesewas")          long    amountPesewas,
        @JsonProperty("amount_cedis")            String  amountCedis,
        @JsonProperty("running_balance_pesewas") long    runningBalancePesewas,
        @JsonProperty("running_balance_cedis")   String  runningBalanceCedis,
        @JsonProperty("transaction_reference")   String  transactionReference,
        @JsonProperty("transaction_type")        String  transactionType,
        String                                           narrative,
        @JsonProperty("created_at")              Instant createdAt
) {
    public static StatementEntryDto of(UUID entryId, String direction,
                                        long amountPesewas, long runningBalancePesewas,
                                        String transactionReference, String transactionType,
                                        String narrative, Instant createdAt) {
        return new StatementEntryDto(
                entryId, direction,
                amountPesewas,        toCedis(amountPesewas),
                runningBalancePesewas, toCedis(runningBalancePesewas),
                transactionReference, transactionType,
                narrative, createdAt);
    }

    private static String toCedis(long pesewas) {
        return BigDecimal.valueOf(pesewas)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
    }
}
