package com.stash.platform.vault.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class EarlyExitReleaseMetrics {

    private final Counter released;
    private final Counter failed;
    private final Counter p0Alerts;
    private final AtomicLong pendingCount = new AtomicLong(0);

    public EarlyExitReleaseMetrics(MeterRegistry registry) {
        this.released  = Counter.builder("early_exit_releases_total")
                .description("Successful early-exit releases processed")
                .register(registry);
        this.failed    = Counter.builder("early_exit_release_failures_total")
                .description("Failed early-exit release attempts")
                .register(registry);
        this.p0Alerts  = Counter.builder("early_exit_p0_alerts_total")
                .description("Early-exit requests escalated to P0 after max attempts")
                .register(registry);
        io.micrometer.core.instrument.Gauge
                .builder("early_exit_pending_due_count", pendingCount, AtomicLong::get)
                .description("Due early-exit requests waiting to be processed")
                .register(registry);
    }

    public void recordReleased()             { released.increment(); }
    public void recordFailed()               { failed.increment(); }
    public void recordP0Alert()              { p0Alerts.increment(); }
    public void updatePendingCount(long n)   { pendingCount.set(n); }
}
