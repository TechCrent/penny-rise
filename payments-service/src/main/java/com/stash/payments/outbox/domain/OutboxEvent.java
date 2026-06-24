package com.stash.payments.outbox.domain;

import java.util.UUID;

/**
 * Marker interface for all domain events that travel through the outbox.
 *
 * <p>Every implementing class must declare:
 * <ul>
 *   <li>{@link #getEventType()} — fully-qualified event name, e.g.
 *       {@code payments.deposit.completed}</li>
 *   <li>{@link #getRoutingKey()} — RabbitMQ routing key, e.g.
 *       {@code deposit.completed}</li>
 *   <li>{@link #getAggregateType()} — e.g. {@code VAULT_DEPOSIT}</li>
 *   <li>{@link #getAggregateId()} — UUID of the business object</li>
 * </ul>
 *
 * <p>Schema version is declared on the concrete class via
 * {@link SchemaVersion} annotation; defaults to {@code "1.0"}.
 */
public interface OutboxEvent {
    String getEventType();
    String getRoutingKey();
    String getAggregateType();
    UUID   getAggregateId();
}
