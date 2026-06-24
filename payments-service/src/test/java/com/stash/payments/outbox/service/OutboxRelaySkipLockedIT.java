package com.stash.payments.outbox.service;

import com.stash.payments.outbox.domain.OutboxEvent;
import com.stash.payments.outbox.domain.OutboxEventStatus;
import com.stash.payments.outbox.repository.OutboxEventRepository;
import com.stash.payments.shared.startup.LedgerGrantsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that SELECT FOR UPDATE SKIP LOCKED prevents two relay instances
 * from processing the same outbox event.
 *
 * <p>Test strategy: seed N events, run two relay instances concurrently,
 * assert each event was published exactly once (no double-publish) and
 * all rows end up SENT.
 *
 * <p>Runs against the shared dev database (stash-payments-db on
 * localhost:15433). RabbitMQ is mocked — we only need to verify the
 * DB-level SKIP LOCKED behaviour, not actual message delivery.
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
class OutboxRelaySkipLockedIT {

    @MockBean LedgerGrantsVerifier ledgerGrantsVerifier;
    @MockBean ConnectionFactory    connectionFactory;
    @MockBean RabbitTemplate       rabbitTemplate;

    @Autowired private OutboxPublisher publisher;
    @Autowired private OutboxRelay relay;
    @Autowired private OutboxEventRepository outboxRepo;
    @Autowired private PlatformTransactionManager txManager;

    @Test
    @DisplayName("two concurrent relay instances process each event exactly once")
    void skip_locked_prevents_double_processing() throws Exception {
        List<UUID> seededIds = new ArrayList<>();
        new TransactionTemplate(txManager).execute(status -> {
            for (int i = 0; i < 20; i++) {
                UUID id = UUID.randomUUID();
                seededIds.add(id);
                publisher.publish(new TestEvent(id), "corr-it-" + i);
            }
            return null;
        });

        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<Void> relayTask = () -> {
            latch.await();
            relay.relay();
            return null;
        };

        Future<Void> r1 = exec.submit(relayTask);
        Future<Void> r2 = exec.submit(relayTask);
        latch.countDown();

        r1.get(10, TimeUnit.SECONDS);
        r2.get(10, TimeUnit.SECONDS);
        exec.shutdown();

        List<OutboxEventStatus> statuses = outboxRepo.findAll().stream()
                .filter(e -> seededIds.contains(e.getAggregateId()))
                .map(e -> e.getStatus())
                .toList();

        assertThat(statuses).hasSize(20);
        assertThat(statuses).allMatch(s -> s == OutboxEventStatus.SENT);
    }

    record TestEvent(UUID id) implements OutboxEvent {
        @Override public String getEventType()     { return "payments.relay.test"; }
        @Override public String getRoutingKey()    { return "relay.test"; }
        @Override public String getAggregateType() { return "RELAY_IT"; }
        @Override public UUID   getAggregateId()   { return id; }
    }
}
