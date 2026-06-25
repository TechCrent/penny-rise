package com.stash.payments.ledger.exception;

/**
 * Thrown when a caller attempts to post a ledger transaction whose
 * debit entries do not sum to the same total as its credit entries.
 *
 * <p>This exception is thrown BEFORE any database write. No partial
 * state is ever committed when this is thrown.
 */
public class LedgerImbalanceException extends RuntimeException {

    private final long totalDebits;
    private final long totalCredits;

    public LedgerImbalanceException(long totalDebits, long totalCredits) {
        super(String.format(
                "Ledger imbalance: DEBIT total %d pesewas != CREDIT total %d pesewas. " +
                "Delta: %d pesewas. Transaction rejected before any write.",
                totalDebits, totalCredits, Math.abs(totalDebits - totalCredits)));
        this.totalDebits  = totalDebits;
        this.totalCredits = totalCredits;
    }

    public long getTotalDebits()  { return totalDebits; }
    public long getTotalCredits() { return totalCredits; }
}
