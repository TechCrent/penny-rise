package com.stash.platform.transfer.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.transfer.domain.PeerTransferEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record TransferListItemResponse(
        UUID                                               id,
        @JsonProperty("transaction_reference") String     transactionReference,
        @JsonProperty("amount")                long       amount,
        @JsonProperty("amount_cedis")          String     amountCedis,
        @JsonProperty("fee_amount")            long       feeAmount,
        @JsonProperty("fee_amount_cedis")      String     feeAmountCedis,
        String                                             direction,
        @JsonProperty("counterparty_user_id")  UUID       counterpartyUserId,
        @JsonProperty("counterparty_display_name") String counterpartyDisplayName,
        String                                             narrative,
        String                                             status,
        @JsonProperty("created_at")            Instant    createdAt,
        @JsonProperty("completed_at")          Instant    completedAt
) {
    public static TransferListItemResponse of(PeerTransferEntity t,
                                               UUID callerId,
                                               String counterpartyName) {
        boolean isSent        = callerId.equals(t.getSenderUserId());
        String  direction     = isSent ? "SENT" : "RECEIVED";
        UUID    counterpartyId = isSent ? t.getRecipientUserId() : t.getSenderUserId();

        return new TransferListItemResponse(
                t.getId(),
                t.getTransactionId() != null ? t.getTransactionId().toString() : null,
                t.getAmount(), toCedis(t.getAmount()),
                t.getFeeAmount(), toCedis(t.getFeeAmount()),
                direction, counterpartyId, counterpartyName,
                t.getNote(), t.getStatus(),
                t.getCreatedAt(), t.getCompletedAt()
        );
    }

    private static String toCedis(long pesewas) {
        return BigDecimal.valueOf(pesewas)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.FLOOR)
                .toPlainString();
    }
}
