package com.stash.payments.transaction.event;

import com.stash.payments.outbox.domain.OutboxEvent;

import java.util.UUID;

/**
 * Published when a deposit is successfully initiated with Paystack.
 * Written to the outbox by the deposit endpoint (v0.3-017) alongside
 * the PENDING transaction row. The relay publishes it to RabbitMQ;
 * the webhook handler (v0.3-018) publishes a second event
 * (DepositCompletedEvent) once Paystack confirms.
 */
public record DepositInitiatedEvent(
        UUID   transactionId,
        UUID   userId,
        UUID   vaultId,
        long   grossAmountPesewas,
        String transactionReference
) implements OutboxEvent {

    @Override public String getEventType()     { return "payments.deposit.initiated"; }
    @Override public String getRoutingKey()    { return "deposit.initiated"; }
    @Override public String getAggregateType() { return "VAULT_DEPOSIT"; }
    @Override public UUID   getAggregateId()   { return transactionId; }
}
