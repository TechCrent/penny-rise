package com.stash.payments.ledger.service;

import com.stash.payments.ledger.repository.LedgerEntryRepository;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the double-entry invariant across all POSTED ledger transactions.
 *
 * <p>For every POSTED {@code ledger_transactions} row, SUM(DEBIT entries) must
 * exactly equal SUM(CREDIT entries). Any deviation is a P0 incident.
 *
 * <p>Also flags PENDING transactions older than
 * {@code stash.ledger.integrity.stale-pending-threshold-minutes} (default 30),
 * which indicate orphaned deposits or withdrawals where the Paystack webhook
 * never arrived.
 */
@Service
public class LedgerIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(LedgerIntegrityService.class);

    private final LedgerEntryRepository entryRepo;
    private final TransactionRepository transactionRepo;
    private final Clock                 clock;
    private final Duration              stalePendingThreshold;

    public LedgerIntegrityService(
            LedgerEntryRepository entryRepo,
            TransactionRepository transactionRepo,
            Clock clock,
            @Value("${stash.ledger.integrity.stale-pending-threshold-minutes:30}")
            int stalePendingThresholdMinutes) {
        this.entryRepo             = entryRepo;
        this.transactionRepo       = transactionRepo;
        this.clock                 = clock;
        this.stalePendingThreshold = Duration.ofMinutes(stalePendingThresholdMinutes);
    }

    /**
     * Runs the integrity check for transactions created yesterday (00:00–23:59 UTC).
     * This is the default nightly check — fast, focused on recent data.
     */
    @Transactional(readOnly = true)
    public IntegrityCheckResult runNightlyCheck() {
        Instant now       = Instant.now(clock);
        Instant yesterday = now.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        Instant today     = now.truncatedTo(ChronoUnit.DAYS);

        log.info("LedgerIntegrityService: running nightly check for window {} to {}", yesterday, today);
        return runCheck(yesterday, today);
    }

    /**
     * Runs the integrity check across the full transaction history.
     * Intended for manual invocation after a data migration — not the scheduled run.
     */
    @Transactional(readOnly = true)
    public IntegrityCheckResult runFullHistoryCheck() {
        Instant epoch = Instant.EPOCH;
        Instant now   = Instant.now(clock);
        log.warn("LedgerIntegrityService: running FULL HISTORY check ({} to {})", epoch, now);
        return runCheck(epoch, now);
    }

    // ── Internal check implementation ─────────────────────────────────────

    private IntegrityCheckResult runCheck(Instant from, Instant to) {
        Instant start = Instant.now(clock);

        // ── Double-entry invariant check ──────────────────────────────────
        List<Object[]> imbalancedRows = entryRepo.findImbalancedTransactions(from, to);
        List<IntegrityCheckResult.DriftedTransaction> drifted = new ArrayList<>();

        for (Object[] row : imbalancedRows) {
            String txnId       = String.valueOf(row[0]);
            String reference   = (String) row[1];
            long   totalDebit  = ((Number) row[2]).longValue();
            long   totalCredit = ((Number) row[3]).longValue();
            long   drift       = Math.abs(totalDebit - totalCredit);

            drifted.add(new IntegrityCheckResult.DriftedTransaction(
                    txnId, reference, totalDebit, totalCredit));

            log.error("[P0_ALERT] LEDGER DRIFT DETECTED: " +
                      "txn_id={} reference={} total_debit={}p total_credit={}p drift={}p — " +
                      "THIS IS A P0 INCIDENT. Investigate immediately.",
                    txnId, reference, totalDebit, totalCredit, drift);
        }

        // ── Stale PENDING transaction check ───────────────────────────────
        Instant staleThresholdTs  = Instant.now(clock).minus(stalePendingThreshold);
        long    stalePendingCount = transactionRepo.countStalePendingTransactions(staleThresholdTs);

        if (stalePendingCount > 0) {
            List<Object[]> staleRows = transactionRepo.findStalePendingTransactions(staleThresholdTs);
            for (Object[] row : staleRows) {
                log.warn("[STALE_PENDING] PENDING transaction older than {}: " +
                         "reference={} type={} created_at={} paystack_ref={}",
                        stalePendingThreshold, row[0], row[1], row[2], row[3]);
            }
            if (staleRows.size() < stalePendingCount) {
                log.warn("[STALE_PENDING] {} additional stale PENDING transactions not logged " +
                         "(showing first 50 only)", stalePendingCount - staleRows.size());
            }
        }

        // ── Count transactions checked ─────────────────────────────────────
        long transactionsChecked = entryRepo.countPostedTransactionsInWindow(from, to);

        long durationMs = Duration.between(start, Instant.now(clock)).toMillis();

        IntegrityCheckResult result = new IntegrityCheckResult(
                start, transactionsChecked, drifted, stalePendingCount, durationMs);

        if (result.isClean()) {
            log.info("LedgerIntegrityService: check complete — {} transactions verified, " +
                     "0 drift, 0 stale PENDING. Duration: {}ms",
                    transactionsChecked, durationMs);
        } else {
            log.error("LedgerIntegrityService: check complete — {} DRIFTED transactions, " +
                      "{} stale PENDING transactions. Duration: {}ms. SEE P0_ALERT ENTRIES ABOVE.",
                    drifted.size(), stalePendingCount, durationMs);
        }

        return result;
    }
}
