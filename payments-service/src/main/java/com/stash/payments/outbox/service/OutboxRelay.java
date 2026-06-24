package com.stash.payments.outbox.service;

import com.stash.payments.outbox.domain.OutboxDeadLetter;
import com.stash.payments.outbox.domain.OutboxEventEntity;
import com.stash.payments.outbox.metrics.OutboxMetrics;
import com.stash.payments.outbox.repository.OutboxDeadLetterRepository;
import com.stash.payments.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Relay worker for the transactional outbox pattern.
 *
 * <p>Polls {@code outbox.outbox_events} every 2 seconds for PENDING rows,
 * publishes each to RabbitMQ, and marks it SENT. Rows that fail after
 * {@value MAX_ATTEMPTS} attempts are moved to the dead-letter table and
 * marked FAILED on the source row.
 *
 * <p><strong>Multi-instance safety:</strong> the poll query uses
 * {@code SELECT FOR UPDATE SKIP LOCKED}. Multiple relay instances running
 * in parallel each claim a distinct batch — no row is processed twice.
 *
 * <p><strong>Exchange derivation:</strong> the {@code routing_key} column
 * on the outbox row is the pre-computed RabbitMQ routing key (set by the
 * event class at write time). The exchange is derived from {@code event_type}
 * by taking the first segment plus {@code .events}:
 * {@code payments.deposit.completed} → exchange {@code payments.events}.
 *
 * <p><strong>At-least-once delivery:</strong> if the relay publishes to
 * RabbitMQ but crashes before committing the SENT status update, the row
 * remains PENDING and is republished on the next poll. Consumers must be
 * idempotent (they deduplicate on {@code event_id} in message headers).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    static final int BATCH_SIZE    = 100;
    static final int MAX_ATTEMPTS  = 5;

    private final OutboxEventRepository      outboxRepo;
    private final OutboxDeadLetterRepository deadLetterRepo;
    private final RabbitTemplate             rabbitTemplate;
    private final OutboxMetrics              metrics;
    private final Clock                      clock;

    public OutboxRelay(OutboxEventRepository outboxRepo,
                       OutboxDeadLetterRepository deadLetterRepo,
                       RabbitTemplate rabbitTemplate,
                       OutboxMetrics metrics,
                       Clock clock) {
        this.outboxRepo     = outboxRepo;
        this.deadLetterRepo = deadLetterRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.metrics        = metrics;
        this.clock          = clock;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay() {
        List<OutboxEventEntity> batch = outboxRepo.findPendingBatch(BATCH_SIZE);
        metrics.updatePendingCount(batch.size());

        if (batch.isEmpty()) return;

        log.debug("OutboxRelay: processing batch of {} events", batch.size());

        for (OutboxEventEntity event : batch) {
            processOne(event);
        }
    }

    private void processOne(OutboxEventEntity event) {
        Instant now = Instant.now(clock);
        try {
            publish(event);
            event.markSent(now);
            outboxRepo.save(event);
            metrics.recordPublished();

            log.debug("OutboxRelay: published event={} aggregate={}:{}",
                    event.getEventType(), event.getAggregateType(), event.getAggregateId());

        } catch (Exception ex) {
            metrics.recordFailed();
            int attemptsAfterThis = event.getAttempts() + 1;

            if (attemptsAfterThis >= MAX_ATTEMPTS) {
                deadLetter(event, ex, now);
            } else {
                event.incrementAttempt(now);
                outboxRepo.save(event);
                log.warn("OutboxRelay: publish failed for event={} attempt={}/{}; will retry. error={}",
                        event.getId(), attemptsAfterThis, MAX_ATTEMPTS, ex.getMessage());
            }
        }
    }

    private void publish(OutboxEventEntity event) {
        String exchange   = deriveExchange(event.getEventType());
        String routingKey = event.getRoutingKey();

        var message = MessageBuilder
                .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("event_id",       event.getId().toString())
                .setHeader("event_type",     event.getEventType())
                .setHeader("schema_version", event.getSchemaVersion())
                .setHeader("aggregate_type", event.getAggregateType())
                .setHeader("aggregate_id",   event.getAggregateId().toString())
                .setHeader("correlation_id", event.getCorrelationId())
                .build();

        rabbitTemplate.send(exchange, routingKey, message);
    }

    private void deadLetter(OutboxEventEntity event, Exception cause, Instant now) {
        log.error("OutboxRelay: event={} exhausted {} attempts — moving to dead-letter. " +
                  "event_type={} aggregate={}:{} last_error={}",
                event.getId(), MAX_ATTEMPTS,
                event.getEventType(), event.getAggregateType(), event.getAggregateId(),
                cause.getMessage());

        event.recordFailedAttempt(now);
        outboxRepo.save(event);

        OutboxDeadLetter deadLetter = new OutboxDeadLetter(event, cause.getMessage(), now);
        deadLetterRepo.save(deadLetter);
        metrics.recordDeadLettered();
    }

    /**
     * Derives the RabbitMQ exchange from the event type.
     * {@code payments.deposit.completed} → {@code payments.events}
     * {@code kyc.submission.approved}   → {@code kyc.events}
     */
    static String deriveExchange(String eventType) {
        int dot = eventType.indexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "event_type must be in format {domain}.{entity}.{verb}: " + eventType);
        }
        return eventType.substring(0, dot) + ".events";
    }
}
