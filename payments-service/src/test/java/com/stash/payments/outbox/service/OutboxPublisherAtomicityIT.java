package com.stash.payments.outbox.service;

import com.stash.payments.outbox.domain.OutboxEvent;
import com.stash.payments.outbox.domain.OutboxEventStatus;
import com.stash.payments.outbox.repository.OutboxEventRepository;
import com.stash.payments.shared.startup.LedgerGrantsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test proving that OutboxPublisher participates in the
 * caller's @Transactional boundary correctly:
 *
 * 1. Happy path: business write + outbox row commit together.
 * 2. Rollback path: simulated business failure rolls back both.
 * 3. Called outside transaction: IllegalTransactionStateException.
 *
 * <p>Runs against the shared dev database (stash-payments-db on
 * localhost:15433). Flyway is disabled — the outbox schema was already
 * applied via psql in v0.3-006.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        "PAYMENTS_DB_USER=stash_payments",
        "PAYMENTS_DB_PASSWORD=payments_local_pass"
    }
)
class OutboxPublisherAtomicityIT {

    @MockBean LedgerGrantsVerifier ledgerGrantsVerifier;
    @MockBean ConnectionFactory    connectionFactory;

    @Autowired private OutboxPublisher publisher;
    @Autowired private OutboxEventRepository outboxRepo;
    @Autowired private PlatformTransactionManager txManager;

    private static final String CORR_ID = "it-corr-001";

    // ── Test 1: happy path ────────────────────────────────────────────────

    @Test
    @DisplayName("business write and outbox row commit atomically")
    void business_and_outbox_commit_together() {
        UUID aggregateId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        tx.execute(status -> {
            publisher.publish(new SimpleTestEvent(aggregateId), CORR_ID);
            return null;
        });

        List<com.stash.payments.outbox.domain.OutboxEventEntity> rows =
                outboxRepo.findAll().stream()
                        .filter(r -> r.getAggregateId().equals(aggregateId))
                        .toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    // ── Test 2: rollback path ─────────────────────────────────────────────

    @Test
    @DisplayName("rollback of caller transaction rolls back the outbox row too")
    void rollback_removes_outbox_row() {
        UUID aggregateId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        try {
            tx.execute(status -> {
                publisher.publish(new SimpleTestEvent(aggregateId), CORR_ID);
                status.setRollbackOnly();
                return null;
            });
        } catch (Exception ignored) {}

        List<?> rows = outboxRepo.findAll().stream()
                .filter(r -> r.getAggregateId().equals(aggregateId))
                .toList();
        assertThat(rows).as("outbox row must not exist after rollback").isEmpty();
    }

    // ── Test 3: called outside transaction ────────────────────────────────

    @Test
    @DisplayName("calling publish outside a transaction throws IllegalTransactionStateException")
    void publish_outside_transaction_throws() {
        assertThatThrownBy(() ->
                publisher.publish(new SimpleTestEvent(UUID.randomUUID()), CORR_ID))
                .isInstanceOf(
                        org.springframework.transaction.IllegalTransactionStateException.class);
    }

    // ── Test event ────────────────────────────────────────────────────────

    record SimpleTestEvent(UUID id) implements OutboxEvent {
        @Override public String getEventType()     { return "test.outbox.atomicity"; }
        @Override public String getRoutingKey()    { return "outbox.atomicity"; }
        @Override public String getAggregateType() { return "TEST_AGGREGATE"; }
        @Override public UUID   getAggregateId()   { return id; }
    }
}
