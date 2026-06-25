package com.stash.payments.ledger.exception;

/**
 * Thrown when the nightly integrity job detects a posted transaction
 * whose entries do not balance. This should never happen in normal
 * operation — it indicates either a bug in LedgerService or a
 * manual database mutation.
 */
public class LedgerInvariantViolationException extends RuntimeException {
    public LedgerInvariantViolationException(String message) {
        super(message);
    }
}
