package com.stash.payments.ledger.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerIntegrityMetricsTest {

    private final SimpleMeterRegistry   registry = new SimpleMeterRegistry();
    private final LedgerIntegrityMetrics metrics  = new LedgerIntegrityMetrics(registry);

    @Test
    @DisplayName("all four gauges are registered on construction")
    void four_gauges_are_registered() {
        assertThat(registry.find("ledger_integrity_check_drift_count").gauge()).isNotNull();
        assertThat(registry.find("ledger_integrity_check_last_run_at").gauge()).isNotNull();
        assertThat(registry.find("ledger_integrity_check_stale_pending_count").gauge()).isNotNull();
        assertThat(registry.find("ledger_integrity_check_transactions_checked").gauge()).isNotNull();
    }

    @Test
    @DisplayName("all four gauges start at zero before any run")
    void gauges_start_at_zero() {
        assertThat(registry.find("ledger_integrity_check_drift_count").gauge().value()).isEqualTo(0.0);
        assertThat(registry.find("ledger_integrity_check_last_run_at").gauge().value()).isEqualTo(0.0);
        assertThat(registry.find("ledger_integrity_check_stale_pending_count").gauge().value()).isEqualTo(0.0);
        assertThat(registry.find("ledger_integrity_check_transactions_checked").gauge().value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("recordRunComplete updates all four gauges")
    void record_run_complete_updates_gauges() {
        metrics.recordRunComplete(2L, 3L, 500L, 1_750_000_000L);

        assertThat(registry.find("ledger_integrity_check_drift_count").gauge().value()).isEqualTo(2.0);
        assertThat(registry.find("ledger_integrity_check_stale_pending_count").gauge().value()).isEqualTo(3.0);
        assertThat(registry.find("ledger_integrity_check_transactions_checked").gauge().value()).isEqualTo(500.0);
        assertThat(registry.find("ledger_integrity_check_last_run_at").gauge().value()).isEqualTo(1_750_000_000.0);
    }

    @Test
    @DisplayName("recordRunComplete with zero drift after a prior drifted run clears back to zero")
    void record_run_complete_clears_drift_to_zero() {
        metrics.recordRunComplete(5L, 0L, 100L, 1_000L);
        metrics.recordRunComplete(0L, 0L, 200L, 2_000L);

        assertThat(registry.find("ledger_integrity_check_drift_count").gauge().value()).isEqualTo(0.0);
        assertThat(registry.find("ledger_integrity_check_transactions_checked").gauge().value()).isEqualTo(200.0);
    }
}
