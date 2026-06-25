package com.stash.payments.ledger.event;

import com.stash.payments.outbox.domain.OutboxEvent;

import java.util.UUID;

/**
 * Published to the outbox when a ledger transaction is successfully posted.
 * Downstream consumers (Audit, Notification) react to this to record
 * the accounting event in their own models.
 */
public record LedgerTransactionPostedEvent(
        UUID   ledgerTransactionId,
        String transactionType,
        String transactionReference,
        long   totalAmountPesewas,
        String correlationId
) implements OutboxEvent {

    @Override public String getEventType()     { return "payments.ledger.transaction.posted"; }
    @Override public String getRoutingKey()    { return "ledger.transaction.posted"; }
    @Override public String getAggregateType() { return "LEDGER_TRANSACTION"; }
    @Override public UUID   getAggregateId()   { return ledgerTransactionId; }
}
