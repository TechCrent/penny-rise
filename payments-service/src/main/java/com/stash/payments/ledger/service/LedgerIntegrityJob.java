package com.stash.payments.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Scheduled wrapper that runs the nightly ledger integrity check and
 * emits Prometheus metrics from the result.
 *
 * <p>Schedule: 02:00 UTC daily — Ghana low-volume maintenance window (05:00 WAT).
 *
 * <p>Idempotent: checks a fixed window (yesterday's transactions) and produces
 * the same result whether run once or multiple times on the same day.
 *
 * <p>In test environments, disable scheduling via
 * {@code spring.task.scheduling.enabled=false} and call
 * {@link LedgerIntegrityService#runNightlyCheck()} directly.
 */
@Component
public class LedgerIntegrityJob {

    private static final Logger log = LoggerFactory.getLogger(LedgerIntegrityJob.class);

    private final LedgerIntegrityService service;
    private final LedgerIntegrityMetrics metrics;
    private final Clock                  clock;

    public LedgerIntegrityJob(LedgerIntegrityService service,
                               LedgerIntegrityMetrics metrics,
                               Clock clock) {
        this.service = service;
        this.metrics = metrics;
        this.clock   = clock;
    }

    @Scheduled(cron = "0 0 2 * * *")   // 02:00 UTC daily
    public void runNightlyIntegrityCheck() {
        log.info("LedgerIntegrityJob: starting nightly check at {}", Instant.now(clock));
        try {
            IntegrityCheckResult result = service.runNightlyCheck();
            metrics.recordRunComplete(
                    result.driftedTransactions().size(),
                    result.stalePendingCount(),
                    result.transactionsChecked(),
                    Instant.now(clock).getEpochSecond()
            );
        } catch (Exception e) {
            log.error("LedgerIntegrityJob: job threw an unexpected exception — " +
                      "integrity check may be incomplete. error={}",
                    e.getMessage(), e);
            // Update lastRunAt even on failure so the "job stopped running" alert does
            // not fire — the job ran, it just errored (different escalation path).
            metrics.recordRunComplete(0L, 0L, 0L, Instant.now(clock).getEpochSecond());
            throw e;
        }
    }
}
