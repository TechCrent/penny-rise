package com.stash.payments.ledger.service;

import com.stash.payments.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Computes the balance of a ledger account as SUM(CREDIT) - SUM(DEBIT)
 * over its posted entries.
 *
 * <p><strong>Balance locking for withdrawals:</strong> call
 * {@link #computeBalanceWithLock(UUID)} inside an active transaction
 * to hold a row-level lock on the account for the duration of the
 * balance check + ledger write. This prevents two concurrent withdrawals
 * from both passing a balance check that only one should pass.
 */
@Service
public class BalanceService {

    private final LedgerEntryRepository entryRepository;

    public BalanceService(LedgerEntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    /**
     * Computes the account balance without acquiring a lock.
     * Safe for read-only queries (e.g. the balance endpoint).
     */
    @Transactional(readOnly = true)
    public long computeBalance(UUID accountId) {
        return queryBalance(accountId);
    }

    /**
     * Computes the account balance in a single query without acquiring a lock.
     * Used by the balance endpoint (read-only; high frequency).
     *
     * @param accountId the ledger account to query
     * @return net balance in pesewas; 0 if no entries exist (new account)
     */
    @Transactional(readOnly = true)
    public long computeBalanceFast(UUID accountId) {
        return entryRepository.computeNetBalance(accountId);
    }

    /**
     * Computes the account balance inside the caller's transaction,
     * acquiring a SELECT FOR UPDATE lock on the account row.
     *
     * <p>Must be called from within an active {@code @Transactional} context.
     * The lock is held until the outer transaction commits or rolls back.
     *
     * <p>Used exclusively by the withdrawal flow to serialise concurrent
     * withdrawals against the same account.
     */
    @Transactional
    public long computeBalanceWithLock(UUID accountId) {
        entryRepository.lockAccount(accountId);
        return queryBalance(accountId);
    }

    private long queryBalance(UUID accountId) {
        Long credits = entryRepository.sumByAccountIdAndDirection(accountId, "CREDIT");
        Long debits  = entryRepository.sumByAccountIdAndDirection(accountId, "DEBIT");
        return (credits != null ? credits : 0L) - (debits != null ? debits : 0L);
    }
}
