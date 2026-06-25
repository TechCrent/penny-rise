package com.stash.payments.ledger.exception;

public class InsufficientBalanceException extends RuntimeException {

    private final long available;
    private final long requested;

    public InsufficientBalanceException(long available, long requested) {
        super(String.format(
                "Insufficient balance: requested %dp but only %dp available.",
                requested, available));
        this.available = available;
        this.requested = requested;
    }

    public long getAvailable() { return available; }
    public long getRequested() { return requested; }
}
