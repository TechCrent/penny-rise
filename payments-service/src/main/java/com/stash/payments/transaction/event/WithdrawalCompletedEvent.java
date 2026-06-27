package com.stash.payments.transaction.event;

import com.stash.payments.outbox.domain.OutboxEvent;
import java.util.UUID;

public record WithdrawalCompletedEvent(
        UUID   transactionId,
        UUID   ledgerTransactionId,
        UUID   userId,
        UUID   sourceAccountId,
        long   amountPesewas,
        String transactionReference,
        String correlationId
) implements OutboxEvent {

    @Override public String getEventType()     { return "payments.withdrawal.completed"; }
    @Override public String getRoutingKey()    { return "withdrawal.completed"; }
    @Override public String getAggregateType() { return "VAULT_WITHDRAWAL"; }
    @Override public UUID   getAggregateId()   { return transactionId; }
}
