package com.stash.payments.ledger.service;

import com.stash.payments.ledger.repository.LedgerEntryRepository;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LedgerIntegrityServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final LedgerEntryRepository  entryRepo       = Mockito.mock(LedgerEntryRepository.class);
    private final TransactionRepository  transactionRepo = Mockito.mock(TransactionRepository.class);
    private final LedgerIntegrityService service =
            new LedgerIntegrityService(entryRepo, transactionRepo, FIXED_CLOCK, 30);

    @BeforeEach
    void setUp() {
        when(transactionRepo.countStalePendingTransactions(any())).thenReturn(0L);
        when(transactionRepo.findStalePendingTransactions(any())).thenReturn(List.of());
        when(entryRepo.countPostedTransactionsInWindow(any(), any())).thenReturn(100L);
    }

    // ── Happy path: no drift ──────────────────────────────────────────────

    @Test
    @DisplayName("all transactions balanced: zero drift, result is clean")
    void all_balanced_zero_drift() {
        when(entryRepo.findImbalancedTransactions(any(), any())).thenReturn(List.of());

        IntegrityCheckResult result = service.runNightlyCheck();

        assertThat(result.driftedTransactions()).isEmpty();
        assertThat(result.stalePendingCount()).isEqualTo(0L);
        assertThat(result.transactionsChecked()).isEqualTo(100L);
        assertThat(result.isClean()).isTrue();
    }

    @Test
    @DisplayName("zero transactions in window: clean result with 0 checked")
    void empty_window_is_clean() {
        when(entryRepo.findImbalancedTransactions(any(), any())).thenReturn(List.of());
        when(entryRepo.countPostedTransactionsInWindow(any(), any())).thenReturn(0L);

        IntegrityCheckResult result = service.runNightlyCheck();

        assertThat(result.isClean()).isTrue();
        assertThat(result.transactionsChecked()).isEqualTo(0L);
    }

    // ── Drift detection ───────────────────────────────────────────────────

    @Test
    @DisplayName("one drifted transaction: reported with correct amounts and drift")
    void one_drifted_transaction_reported() {
        List<Object[]> driftedRows = new ArrayList<>();
        driftedRows.add(new Object[]{
                "aaaaaaaa-0000-0000-0000-000000000001",
                "STSH-202606-DRIFT1",
                10_000L,
                9_000L
        });
        when(entryRepo.findImbalancedTransactions(any(), any())).thenReturn(driftedRows);

        IntegrityCheckResult result = service.runNightlyCheck();

        assertThat(result.driftedTransactions()).hasSize(1);
        assertThat(result.isClean()).isFalse();

        IntegrityCheckResult.DriftedTransaction drifted = result.driftedTransactions().get(0);
        assertThat(drifted.reference()).isEqualTo("STSH-202606-DRIFT1");
        assertThat(drifted.totalDebit()).isEqualTo(10_000L);
        assertThat(drifted.totalCredit()).isEqualTo(9_000L);
        assertThat(drifted.driftAmount()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("multiple drifted transactions: all reported with correct drift amounts")
    void multiple_drifted_transactions_all_reported() {
        List<Object[]> rows = List.of(
                new Object[]{"id-1", "STSH-202606-D001", 5_000L, 4_000L},
                new Object[]{"id-2", "STSH-202606-D002", 8_000L, 8_001L},
                new Object[]{"id-3", "STSH-202606-D003", 0L,     100L}
        );
        when(entryRepo.findImbalancedTransactions(any(), any())).thenReturn(rows);

        IntegrityCheckResult result = service.runNightlyCheck();

        assertThat(result.driftedTransactions()).hasSize(3);
        assertThat(result.isClean()).isFalse();
        assertThat(result.driftedTransactions().get(0).driftAmount()).isEqualTo(1_000L);
        assertThat(result.driftedTransactions().get(1).driftAmount()).isEqualTo(1L);
        assertThat(result.driftedTransactions().get(2).driftAmount()).isEqualTo(100L);
    }

    // ── Stale PENDING ─────────────────────────────────────────────────────

    @Test
    @DisplayName("stale PENDING transactions: reported in result")
    void stale_pending_reported() {
        when(entryRepo.findImbalancedTransactions(any(), any())).thenReturn(List.of());
        when(transactionRepo.countStalePendingTransactions(any())).thenReturn(2L);
        List<Object[]> staleRows = new ArrayList<>();
        staleRows.add(new Object[]{"STSH-202606-STALE1", "DEPOSIT",
                                   Timestamp.from(Instant.parse("2026-06-24T08:00:00Z")),
                                   "pay_ref_stale"});
        staleRows.add(new Object[]{"STSH-202606-STALE2", "WITHDRAWAL",
                                   Timestamp.from(Instant.parse("2026-06-24T07:00:00Z")),
                                   null});
        when(transactionRepo.findStalePendingTransactions(any())).thenReturn(staleRows);

        IntegrityCheckResult result = service.runNightlyCheck();

        assertThat(result.stalePendingCount()).isEqualTo(2L);
        assertThat(result.isClean()).isFalse();
        assertThat(result.driftedTransactions()).isEmpty();
    }

    @Test
    @DisplayName("both drift and stale pending: both reported in same result")
    void drift_and_stale_pending_both_reported() {
        List<Object[]> bothRows = new ArrayList<>();
        bothRows.add(new Object[]{"id-1", "STSH-202606-BOTH1", 5_000L, 4_500L});
        when(entryRepo.findImbalancedTransactions(any(), any())).thenReturn(bothRows);
        when(transactionRepo.countStalePendingTransactions(any())).thenReturn(3L);
        when(transactionRepo.findStalePendingTransactions(any())).thenReturn(List.of());

        IntegrityCheckResult result = service.runNightlyCheck();

        assertThat(result.driftedTransactions()).hasSize(1);
        assertThat(result.stalePendingCount()).isEqualTo(3L);
        assertThat(result.isClean()).isFalse();
    }

    // ── Idempotency ───────────────────────────────────────────────────────

    @Test
    @DisplayName("running the check twice produces the same result (idempotent)")
    void check_is_idempotent() {
        when(entryRepo.findImbalancedTransactions(any(), any())).thenReturn(List.of());

        IntegrityCheckResult r1 = service.runNightlyCheck();
        IntegrityCheckResult r2 = service.runNightlyCheck();

        assertThat(r1.isClean()).isEqualTo(r2.isClean());
        assertThat(r1.driftedTransactions().size()).isEqualTo(r2.driftedTransactions().size());
        assertThat(r1.transactionsChecked()).isEqualTo(r2.transactionsChecked());
    }

    // ── Window bounds ─────────────────────────────────────────────────────

    @Test
    @DisplayName("nightly check queries yesterday's window (midnight to midnight UTC)")
    void nightly_check_queries_yesterday_window() {
        when(entryRepo.findImbalancedTransactions(any(), any())).thenReturn(List.of());

        service.runNightlyCheck();

        // FIXED_CLOCK = 2026-06-24T10:00:00Z
        // yesterday = 2026-06-23T00:00:00Z, today = 2026-06-24T00:00:00Z
        Instant expectedFrom = Instant.parse("2026-06-23T00:00:00Z");
        Instant expectedTo   = Instant.parse("2026-06-24T00:00:00Z");

        verify(entryRepo).findImbalancedTransactions(eq(expectedFrom), eq(expectedTo));
        verify(entryRepo).countPostedTransactionsInWindow(eq(expectedFrom), eq(expectedTo));
    }
}
