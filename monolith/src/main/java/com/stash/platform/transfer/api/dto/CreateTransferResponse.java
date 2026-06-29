package com.stash.platform.transfer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record CreateTransferResponse(
        UUID                                              id,
        @JsonProperty("transaction_reference") String    transactionReference,
        @JsonProperty("amount")                long      amount,
        @JsonProperty("amount_cedis")          String    amountCedis,
        @JsonProperty("fee_amount")            long      feeAmount,
        @JsonProperty("fee_amount_cedis")      String    feeAmountCedis,
        @JsonProperty("total_debited")         long      totalDebited,
        @JsonProperty("total_debited_cedis")   String    totalDebitedCedis,
        @JsonProperty("free_transfers_remaining") int    freeTransfersRemaining,
        @JsonProperty("recipient_user_id")     UUID      recipientUserId,
        String                                           status,
        @JsonProperty("completed_at")          Instant   completedAt
) {
    public static CreateTransferResponse of(UUID id, String txnRef, long amount,
                                             long feeAmount, int freeRemaining,
                                             UUID recipientUserId, Instant completedAt) {
        long total = amount + feeAmount;
        return new CreateTransferResponse(
                id, txnRef, amount, toCedis(amount),
                feeAmount, toCedis(feeAmount),
                total, toCedis(total),
                freeRemaining, recipientUserId,
                "COMPLETED", completedAt
        );
    }

    private static String toCedis(long pesewas) {
        return BigDecimal.valueOf(pesewas)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
    }
}
