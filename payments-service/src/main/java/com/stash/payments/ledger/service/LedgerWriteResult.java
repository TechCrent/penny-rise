package com.stash.payments.ledger.service;

import java.util.UUID;

/**
 * Returned by {@link LedgerService#writeTransaction} on success.
 *
 * @param ledgerTransactionId  the UUID of the committed ledger_transactions row
 * @param transactionReference the customer-visible reference (STSH-yyyymm-XXXXXX)
 * @param totalAmount          the gross amount in pesewas (SUM of DEBIT entries)
 */
public record LedgerWriteResult(
        UUID   ledgerTransactionId,
        String transactionReference,
        long   totalAmount
) {}
