package com.stash.payments.outbox.service;

import com.stash.payments.outbox.domain.OutboxEvent;
import com.stash.payments.outbox.domain.OutboxEventEntity;
import com.stash.payments.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Write-side of the outbox pattern. Persists an {@link OutboxEventEntity}
 * row to {@code outbox.outbox_events} within the caller's existing
 * database transaction.
 *
 * <p><strong>Transaction contract:</strong> this method runs with
 * {@link Propagation#MANDATORY} — it MUST be called from within an active
 * {@code @Transactional} context. If called outside one, Spring throws
 * {@link org.springframework.transaction.IllegalTransactionStateException}
 * at runtime. This is intentional: publishing outside a transaction
 * defeats the outbox pattern entirely (the event would commit before the
 * business change, or vice versa, violating atomicity).
 *
 * <p><strong>Payload immutability:</strong> the {@link OutboxEventEntity}
 * is constructed with {@code updatable = false} on all content columns
 * (payload, event_type, routing_key, aggregate context, schema_version,
 * created_at). After {@code save()}, those fields are never touched again
 * by this publisher. The relay worker ({@code OutboxRelay}, v0.3-011)
 * updates only the four tracking columns (status, attempts, sent_at,
 * last_attempted_at), which are specifically granted UPDATE at the
 * Postgres role level (v0.3-006).
 *
 * <p><strong>Caller pattern:</strong>
 * <pre>{@code
 * @Transactional
 * public void depositCompleted(DepositCompletedEvent event) {
 *     ledgerService.writeTransaction(...);   // ledger entries
 *     outboxPublisher.publish(event, correlationId);  // same transaction
 * }
 * }</pre>
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String DEFAULT_SCHEMA_VERSION = "1.0";

    private final OutboxEventRepository repository;
    private final OutboxPayloadSerializer serializer;
    private final Clock clock;

    public OutboxPublisher(OutboxEventRepository repository,
                           OutboxPayloadSerializer serializer,
                           Clock clock) {
        this.repository = repository;
        this.serializer = serializer;
        this.clock      = clock;
    }

    /**
     * Persists an outbox event row within the caller's transaction.
     *
     * @param event         the domain event to publish
     * @param correlationId the request trace ID; propagated into the outbox row
     *                      so the relay can carry it into the RabbitMQ message headers
     * @return the persisted entity (useful for tests asserting the row was saved)
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         if called outside an active transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEventEntity publish(OutboxEvent event, String correlationId) {
        String payload = serializer.serialize(event);

        OutboxEventEntity entity = new OutboxEventEntity(
                event.getEventType(),
                DEFAULT_SCHEMA_VERSION,
                event.getAggregateType(),
                event.getAggregateId(),
                payload,
                event.getRoutingKey(),
                correlationId,
                Instant.now(clock)
        );

        OutboxEventEntity saved = repository.save(entity);

        log.debug("Outbox event queued: type={} aggregate={}:{} id={}",
                event.getEventType(), event.getAggregateType(),
                event.getAggregateId(), saved.getId());

        return saved;
    }
}
