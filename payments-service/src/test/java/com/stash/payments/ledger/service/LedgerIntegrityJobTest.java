package com.stash.payments.ledger.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LedgerIntegrityJobTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final LedgerIntegrityService service = Mockito.mock(LedgerIntegrityService.class);
    private final LedgerIntegrityMetrics metrics = Mockito.mock(LedgerIntegrityMetrics.class);
    private final LedgerIntegrityJob     job     =
            new LedgerIntegrityJob(service, metrics, FIXED_CLOCK);

    @Test
    @DisplayName("clean run: metrics recorded with zero drift and zero stale")
    void clean_run_records_zero_drift() {
        when(service.runNightlyCheck()).thenReturn(
                new IntegrityCheckResult(Instant.now(FIXED_CLOCK), 500L, List.of(), 0L, 1200L));

        job.runNightlyIntegrityCheck();

        verify(metrics).recordRunComplete(eq(0L), eq(0L), eq(500L), anyLong());
    }

    @Test
    @DisplayName("drifted run: metrics recorded with non-zero drift count")
    void drifted_run_records_drift_count() {
        List<IntegrityCheckResult.DriftedTransaction> drifted = List.of(
                new IntegrityCheckResult.DriftedTransaction("id-1", "STSH-202606-D001", 10_000L, 9_000L)
        );
        when(service.runNightlyCheck()).thenReturn(
                new IntegrityCheckResult(Instant.now(FIXED_CLOCK), 500L, drifted, 0L, 1200L));

        job.runNightlyIntegrityCheck();

        verify(metrics).recordRunComplete(eq(1L), eq(0L), eq(500L), anyLong());
    }

    @Test
    @DisplayName("stale pending: metrics recorded with non-zero stale count")
    void stale_pending_recorded_in_metrics() {
        when(service.runNightlyCheck()).thenReturn(
                new IntegrityCheckResult(Instant.now(FIXED_CLOCK), 200L, List.of(), 3L, 800L));

        job.runNightlyIntegrityCheck();

        verify(metrics).recordRunComplete(eq(0L), eq(3L), eq(200L), anyLong());
    }

    @Test
    @DisplayName("service throws: lastRunAt still updated (job ran, just errored)")
    void service_throws_still_updates_last_run_at() {
        when(service.runNightlyCheck()).thenThrow(new RuntimeException("DB connection lost"));

        assertThatThrownBy(job::runNightlyIntegrityCheck)
                .isInstanceOf(RuntimeException.class);

        // lastRunAt is updated so the "job stopped running" alert does NOT fire
        verify(metrics).recordRunComplete(eq(0L), eq(0L), eq(0L), anyLong());
    }
}
