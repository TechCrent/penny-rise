package com.stash.payments.transaction.event;

import com.stash.payments.outbox.domain.OutboxEvent;

import java.util.UUID;

public record TransferPostedEvent(
        UUID   transactionId,
        UUID   ledgerTransactionId,
        UUID   sourceAccountId,
        UUID   destinationAccountId,
        long   amountPesewas,
        String transactionType,
        String transactionReference,
        String correlationId
) implements OutboxEvent {

    @Override public String getEventType()     { return "payments.transfer.posted"; }
    @Override public String getRoutingKey()    { return "transfer.posted"; }
    @Override public String getAggregateType() { return transactionType; }
    @Override public UUID   getAggregateId()   { return transactionId; }
}
