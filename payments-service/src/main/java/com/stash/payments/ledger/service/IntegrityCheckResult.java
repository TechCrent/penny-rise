package com.stash.payments.ledger.service;

import java.time.Instant;
import java.util.List;

/**
 * Summary result of one integrity check run.
 *
 * @param ranAt               when the check ran
 * @param transactionsChecked total POSTED transactions verified
 * @param driftedTransactions transactions that failed the double-entry invariant
 * @param stalePendingCount   PENDING transactions older than the stale threshold
 * @param durationMs          time taken in milliseconds
 */
public record IntegrityCheckResult(
        Instant                  ranAt,
        long                     transactionsChecked,
        List<DriftedTransaction> driftedTransactions,
        long                     stalePendingCount,
        long                     durationMs
) {
    public boolean isClean() {
        return driftedTransactions.isEmpty() && stalePendingCount == 0;
    }

    public record DriftedTransaction(
            String txnId,
            String reference,
            long   totalDebit,
            long   totalCredit
    ) {
        public long driftAmount() { return Math.abs(totalDebit - totalCredit); }
    }
}
