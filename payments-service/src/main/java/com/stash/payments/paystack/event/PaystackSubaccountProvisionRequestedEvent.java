package com.stash.payments.paystack.event;

import com.stash.payments.outbox.domain.OutboxEvent;

import java.util.UUID;

/**
 * Written to the outbox alongside the USER_WALLET ledger account INSERT.
 * The outbox relay delivers this to the PaystackSubaccountConsumer, which
 * calls Paystack and stores the returned subaccount code.
 *
 * <p>This event is the bridge between the database-side provisioning
 * (safe, atomic) and the Paystack API call (external, non-transactional).
 * Separating them via the outbox means:
 * <ul>
 *   <li>The ledger account is committed even if Paystack is temporarily down.</li>
 *   <li>The outbox relay retries the Paystack call up to 5 times.</li>
 *   <li>If all retries fail, the event is dead-lettered and ops is alerted —
 *       no user is silently left without a subaccount.</li>
 * </ul>
 */
public record PaystackSubaccountProvisionRequestedEvent(
        UUID   ledgerAccountId,
        UUID   userId,
        String userEmail,
        String correlationId
) implements OutboxEvent {

    @Override public String getEventType()     { return "payments.paystack.subaccount.provision.requested"; }
    @Override public String getRoutingKey()    { return "paystack.subaccount.provision.requested"; }
    @Override public String getAggregateType() { return "USER_WALLET"; }
    @Override public UUID   getAggregateId()   { return ledgerAccountId; }
}
