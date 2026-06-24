package com.stash.payments.idempotency.service;

import com.stash.payments.idempotency.service.IdempotencyService.ClaimResult;
import com.stash.payments.shared.startup.LedgerGrantsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency integration test — proves the UNIQUE-constraint race-condition
 * path in {@link IdempotencyService#claim}. Runs against the shared dev
 * database ({@code stash-payments-db} on localhost:15433).
 *
 * <p>Context overrides applied here:
 * <ul>
 *   <li>Flyway disabled — the idempotency schema was already applied to the
 *       dev database via psql in step v0.3-005; re-running would fail on the
 *       V1 version-number collision.</li>
 *   <li>RabbitMQ auto-configuration excluded — no broker needed for this test.</li>
 *   <li>{@link LedgerGrantsVerifier} mocked — startup guard checks outbox
 *       column grants; the test only needs the idempotency layer.</li>
 * </ul>
 *
 * <p>Requires: {@code stash-payments-db} Docker container running (normally
 * started via {@code docker compose up} in the repo root).
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
class IdempotencyServiceConcurrencyIT {

    @MockBean
    LedgerGrantsVerifier ledgerGrantsVerifier;

    @MockBean
    ConnectionFactory connectionFactory;

    @Autowired
    private IdempotencyService service;

    @Test
    @DisplayName("concurrent duplicate requests: exactly one Proceed, one InProgress")
    void concurrent_claims_for_same_key() throws Exception {
        String key  = "concurrent-test-key-" + System.nanoTime();
        String hash = "same-hash-for-both-threads";
        String path = "/api/v1/transactions/deposits";

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<ClaimResult> claimTask = () -> {
            latch.await();  // both threads start simultaneously
            return service.claim(key, hash, path);
        };

        Future<ClaimResult> f1 = executor.submit(claimTask);
        Future<ClaimResult> f2 = executor.submit(claimTask);
        latch.countDown();  // release both

        List<ClaimResult> results = List.of(f1.get(5, TimeUnit.SECONDS),
                                             f2.get(5, TimeUnit.SECONDS));
        executor.shutdown();

        long proceeds   = results.stream().filter(r -> r instanceof ClaimResult.Proceed).count();
        long inProgress = results.stream().filter(r -> r instanceof ClaimResult.InProgress).count();

        assertThat(proceeds).as("exactly one thread should proceed").isEqualTo(1);
        assertThat(inProgress).as("exactly one thread should get InProgress").isEqualTo(1);
    }
}
