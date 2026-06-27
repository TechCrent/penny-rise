package com.stash.payments.outbox.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus metrics for the outbox relay.
 *
 * <p>Metrics exposed:
 * <ul>
 *   <li>{@code outbox_pending_count} — gauge; current PENDING row count
 *       as of the last poll cycle.</li>
 *   <li>{@code outbox_relay_lag_seconds} — gauge; age of the oldest PENDING
 *       row in seconds. Zero when no PENDING rows exist (healthy state).</li>
 *   <li>{@code outbox_events_published_total} — counter; total events
 *       successfully published to RabbitMQ since service start.</li>
 *   <li>{@code outbox_events_failed_total} — counter; total publish
 *       attempts that threw an exception.</li>
 *   <li>{@code outbox_events_dead_lettered_total} — counter; total events
 *       moved to the dead-letter table.</li>
 * </ul>
 *
 * <p>All metrics appear at {@code /actuator/prometheus}.
 */
@Component
public class OutboxMetrics {

    private final AtomicLong pendingCount = new AtomicLong(0);
    private final AtomicLong lagSeconds   = new AtomicLong(0);
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter deadLetteredCounter;

    public OutboxMetrics(MeterRegistry registry) {
        Gauge.builder("outbox_pending_count", pendingCount, AtomicLong::get)
                .description("Number of PENDING outbox events waiting for relay")
                .register(registry);

        Gauge.builder("outbox_relay_lag_seconds", lagSeconds, AtomicLong::get)
                .description("Age of the oldest PENDING outbox event in seconds. " +
                             "Zero when no PENDING rows exist.")
                .register(registry);

        this.publishedCounter = Counter.builder("outbox_events_published")
                .description("Total outbox events successfully published to RabbitMQ")
                .register(registry);

        this.failedCounter = Counter.builder("outbox_events_failed")
                .description("Total outbox publish attempts that failed")
                .register(registry);

        this.deadLetteredCounter = Counter.builder("outbox_events_dead_lettered")
                .description("Total outbox events moved to the dead-letter table")
                .register(registry);
    }

    public void updatePendingCount(long count)      { pendingCount.set(count); }
    public void updateRelayLagSeconds(long seconds) { lagSeconds.set(seconds); }
    public void recordPublished()                   { publishedCounter.increment(); }
    public void recordFailed()                      { failedCounter.increment(); }
    public void recordDeadLettered()                { deadLetteredCounter.increment(); }
}
