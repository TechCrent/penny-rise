package com.stash.payments.ledger.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Balance response.
 *
 * <p>{@code balance_pesewas} is the canonical value — always an integer,
 * never a float. {@code balance_cedis} is a formatted display string
 * (e.g. "125.50") derived from {@code balance_pesewas / 100}.
 * Mobile clients should display {@code balance_cedis} and compute
 * arithmetic from {@code balance_pesewas} to avoid float precision issues.
 *
 * <p>{@code as_of} is the instant the balance was computed, useful for
 * cache invalidation on the mobile side.
 */
public record BalanceResponse(
        @JsonProperty("account_id")       UUID    accountId,
        @JsonProperty("account_type")     String  accountType,
        @JsonProperty("balance_pesewas")  long    balancePesewas,
        @JsonProperty("balance_cedis")    String  balanceCedis,
        String                                    status,
        @JsonProperty("as_of")            Instant asOf
) {
    public static BalanceResponse of(UUID accountId, String accountType,
                                     String status, long balancePesewas,
                                     Instant asOf) {
        String cedis = BigDecimal.valueOf(balancePesewas)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
        return new BalanceResponse(accountId, accountType, balancePesewas,
                cedis, status, asOf);
    }
}
