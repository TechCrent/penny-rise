package com.stash.payments.transaction.event;

import com.stash.payments.outbox.domain.OutboxEvent;

import java.util.UUID;

public record DepositCompletedEvent(
        UUID   transactionId,
        UUID   ledgerTransactionId,
        UUID   userId,
        UUID   ledgerAccountId,
        long   amountPesewas,
        String transactionReference,
        String correlationId
) implements OutboxEvent {

    @Override public String getEventType()     { return "payments.deposit.completed"; }
    @Override public String getRoutingKey()    { return "deposit.completed"; }
    @Override public String getAggregateType() { return "VAULT_DEPOSIT"; }
    @Override public UUID   getAggregateId()   { return transactionId; }
}
