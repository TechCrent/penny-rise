package com.stash.payments.ledger.domain;

import java.util.UUID;

/**
 * Immutable value object representing one side of a double-entry pair.
 * Passed by callers to {@link com.stash.payments.ledger.service.LedgerService#writeTransaction}.
 *
 * @param accountId  the ledger account to debit or credit
 * @param direction  DEBIT or CREDIT
 * @param amount     positive amount in pesewas; never zero, never negative
 * @param narrative  optional human-readable note for this entry
 */
public record EntryRequest(
        UUID accountId,
        EntryDirection direction,
        long amount,
        String narrative
) {
    public EntryRequest {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Entry amount must be positive, got: " + amount);
        }
    }

    /** Convenience constructor without narrative. */
    public static EntryRequest of(UUID accountId, EntryDirection direction, long amount) {
        return new EntryRequest(accountId, direction, amount, null);
    }
}
