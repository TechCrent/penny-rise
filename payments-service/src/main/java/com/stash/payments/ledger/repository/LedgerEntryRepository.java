package com.stash.payments.ledger.repository;

import com.stash.payments.ledger.domain.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Only LedgerService may use this repository — enforced by LedgerWriteArchitectureTest.
public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntryEntity, UUID> {

    List<LedgerEntryEntity> findByLedgerTransactionId(UUID ledgerTransactionId);

    /**
     * Acquires a SELECT FOR UPDATE row-level lock on the ledger account row.
     * Called by BalanceService before computing balance for a withdrawal
     * to serialise concurrent withdrawals against the same account.
     */
    @Query(value = """
            SELECT id FROM ledger.ledger_accounts
            WHERE id = :accountId
            FOR UPDATE
            """, nativeQuery = true)
    UUID lockAccount(@Param("accountId") UUID accountId);

    /**
     * Sums entry amounts by account and direction.
     * Returns NULL when no entries exist (caller must treat as 0).
     */
    @Query(value = """
            SELECT SUM(amount) FROM ledger.ledger_entries
            WHERE account_id = :accountId
              AND direction  = :direction
            """, nativeQuery = true)
    Long sumByAccountIdAndDirection(@Param("accountId") UUID accountId,
                                    @Param("direction") String direction);

    /**
     * Used by the nightly integrity job to verify the double-entry invariant
     * for all POSTED transactions created on a given day.
     */
    @Query(value = """
            SELECT
                lt.id                                                AS txn_id,
                lt.transaction_reference                             AS reference,
                SUM(CASE WHEN le.direction = 'DEBIT'  THEN le.amount ELSE 0 END) AS total_debit,
                SUM(CASE WHEN le.direction = 'CREDIT' THEN le.amount ELSE 0 END) AS total_credit
            FROM ledger.ledger_transactions lt
            JOIN ledger.ledger_entries le ON le.ledger_transaction_id = lt.id
            WHERE lt.status = 'POSTED'
              AND lt.created_at >= :from
              AND lt.created_at <  :to
            GROUP BY lt.id, lt.transaction_reference
            HAVING SUM(CASE WHEN le.direction = 'DEBIT'  THEN le.amount ELSE 0 END)
                <> SUM(CASE WHEN le.direction = 'CREDIT' THEN le.amount ELSE 0 END)
            """, nativeQuery = true)
    List<Object[]> findImbalancedTransactions(@Param("from") Instant from,
                                              @Param("to")   Instant to);

    /**
     * Computes the balance of an account in a single query:
     * SUM of CREDITs minus SUM of DEBITs.
     *
     * <p>Faster than calling sumByAccountIdAndDirection twice because it
     * avoids two round-trips. Used by the balance endpoint where latency matters.
     *
     * <p>Returns 0 if no entries exist (COALESCE handles the NULL case).
     */
    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE 0 END), 0)
              - COALESCE(SUM(CASE WHEN direction = 'DEBIT'  THEN amount ELSE 0 END), 0)
            FROM ledger.ledger_entries
            WHERE account_id = :accountId
            """, nativeQuery = true)
    long computeNetBalance(@Param("accountId") UUID accountId);
}
