package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * A single ledger entry as it appears in a transaction receipt.
 *
 * <p>Amounts are in both pesewas (canonical) and cedis (display).
 * Account type lets the mobile UI label entries meaningfully.
 */
public record TransactionEntryDto(
        String                                    direction,
        @JsonProperty("amount_pesewas")  long     amountPesewas,
        @JsonProperty("amount_cedis")    String   amountCedis,
        @JsonProperty("account_type")    String   accountType,
        @JsonProperty("account_id")      UUID     accountId,
        String                                    narrative
) {
    public static TransactionEntryDto of(String direction, long amount,
                                          String accountType, UUID accountId,
                                          String narrative) {
        String cedis = BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
        return new TransactionEntryDto(direction, amount, cedis, accountType, accountId, narrative);
    }
}
