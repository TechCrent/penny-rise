package com.stash.payments.transaction.event;

import com.stash.payments.outbox.domain.OutboxEvent;
import java.util.UUID;

public record WithdrawalFailedEvent(
        UUID   transactionId,
        UUID   reversalLedgerTransactionId,
        UUID   userId,
        UUID   sourceAccountId,
        long   amountPesewas,
        String transactionReference,
        String paystackFailureReason,
        String correlationId
) implements OutboxEvent {

    @Override public String getEventType()     { return "payments.withdrawal.failed"; }
    @Override public String getRoutingKey()    { return "withdrawal.failed"; }
    @Override public String getAggregateType() { return "VAULT_WITHDRAWAL"; }
    @Override public UUID   getAggregateId()   { return transactionId; }
}
