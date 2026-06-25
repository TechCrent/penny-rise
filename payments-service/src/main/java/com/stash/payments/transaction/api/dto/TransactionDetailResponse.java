package com.stash.payments.transaction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.payments.transaction.domain.TransactionEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionDetailResponse(
        String                                             reference,
        @JsonProperty("transaction_type")   String         transactionType,
        String                                             status,
        @JsonProperty("gross_amount_pesewas") long         grossAmountPesewas,
        @JsonProperty("gross_amount_cedis")   String       grossAmountCedis,
        @JsonProperty("fee_amount_pesewas")   long         feeAmountPesewas,
        @JsonProperty("fee_amount_cedis")     String       feeAmountCedis,
        @JsonProperty("net_amount_pesewas")   long         netAmountPesewas,
        @JsonProperty("net_amount_cedis")     String       netAmountCedis,
        @JsonProperty("initiating_user_id")   UUID         initiatingUserId,
        @JsonProperty("counterparty_user_id") UUID         counterpartyUserId,
        @JsonProperty("external_provider")    String       externalProvider,
        @JsonProperty("external_reference")   String       externalReference,
        @JsonProperty("created_at")           Instant      createdAt,
        @JsonProperty("completed_at")         Instant      completedAt,
        List<TransactionEntryDto>                          entries
) {
    public static TransactionDetailResponse of(TransactionEntity txn,
                                                List<TransactionEntryDto> entries) {
        return new TransactionDetailResponse(
                txn.getReference(),
                txn.getTransactionType(),
                txn.getStatus(),
                txn.getGrossAmount(),
                cedis(txn.getGrossAmount()),
                txn.getFeeAmount(),
                cedis(txn.getFeeAmount()),
                txn.getNetAmount(),
                cedis(txn.getNetAmount()),
                txn.getInitiatingUserId(),
                txn.getCounterpartyUserId(),
                txn.getExternalProvider(),
                txn.getExternalReference(),
                txn.getCreatedAt(),
                txn.getCompletedAt(),
                entries
        );
    }

    private static String cedis(long pesewas) {
        return BigDecimal.valueOf(pesewas)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
    }
}
