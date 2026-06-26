package com.stash.payments.ledger.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus metrics for the nightly ledger integrity check.
 *
 * <ul>
 *   <li>{@code ledger_integrity_check_drift_count} — number of drifted transactions in the
 *       last run; non-zero is a P0 incident.</li>
 *   <li>{@code ledger_integrity_check_last_run_at} — Unix epoch seconds of the last completed
 *       run; alert if older than 26 hours.</li>
 *   <li>{@code ledger_integrity_check_stale_pending_count} — PENDING transactions older than
 *       the stale threshold; indicates orphaned deposits or withdrawals.</li>
 *   <li>{@code ledger_integrity_check_transactions_checked} — total POSTED transactions
 *       verified in the last run.</li>
 * </ul>
 */
@Component
public class LedgerIntegrityMetrics {

    private final AtomicLong driftCount          = new AtomicLong(0);
    private final AtomicLong lastRunAtEpochSecs  = new AtomicLong(0);
    private final AtomicLong stalePendingCount   = new AtomicLong(0);
    private final AtomicLong transactionsChecked = new AtomicLong(0);

    public LedgerIntegrityMetrics(MeterRegistry registry) {
        Gauge.builder("ledger_integrity_check_drift_count", driftCount, AtomicLong::get)
                .description("Number of drifted ledger transactions found in last integrity run. " +
                             "Non-zero = P0 incident.")
                .register(registry);

        Gauge.builder("ledger_integrity_check_last_run_at", lastRunAtEpochSecs, AtomicLong::get)
                .description("Unix epoch seconds of the last completed integrity check run. " +
                             "Alert if older than 26 hours.")
                .register(registry);

        Gauge.builder("ledger_integrity_check_stale_pending_count", stalePendingCount, AtomicLong::get)
                .description("PENDING transactions older than the stale threshold. " +
                             "Indicates orphaned deposits or withdrawals.")
                .register(registry);

        Gauge.builder("ledger_integrity_check_transactions_checked", transactionsChecked, AtomicLong::get)
                .description("Total POSTED transactions verified in the most recent run.")
                .register(registry);
    }

    public void recordRunComplete(long drift, long stale, long checked, long nowEpochSecs) {
        driftCount.set(drift);
        stalePendingCount.set(stale);
        transactionsChecked.set(checked);
        lastRunAtEpochSecs.set(nowEpochSecs);
    }
}
