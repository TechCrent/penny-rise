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
     * Fetches a page of statement entries for cursor-based pagination.
     *
     * <p>Uses the {@code (account_id, created_at DESC)} index. The cursor filters on
     * {@code (created_at < cursorCreatedAt) OR (created_at = cursorCreatedAt AND id < cursorEntryId)}
     * to handle ties correctly.
     *
     * <p>Columns (index-based): 0=entry_id, 1=direction, 2=amount,
     * 3=narrative, 4=created_at, 5=transaction_reference, 6=transaction_type.
     */
    @Query(value = """
            SELECT
                le.id                        AS entry_id,
                le.direction,
                le.amount,
                le.narrative,
                le.created_at,
                lt.transaction_reference,
                lt.transaction_type
            FROM ledger.ledger_entries le
            JOIN ledger.ledger_transactions lt ON lt.id = le.ledger_transaction_id
            WHERE le.account_id = :accountId
              AND (CAST(:fromDate AS timestamptz) IS NULL OR le.created_at >= :fromDate)
              AND (CAST(:toDate   AS timestamptz) IS NULL OR le.created_at <= :toDate)
              AND (
                    CAST(:cursorCreatedAt AS timestamptz) IS NULL
                    OR le.created_at < :cursorCreatedAt
                    OR (le.created_at = :cursorCreatedAt AND le.id < :cursorEntryId)
                  )
            ORDER BY le.created_at DESC, le.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findStatementPage(
            @Param("accountId")       UUID    accountId,
            @Param("fromDate")        Instant fromDate,
            @Param("toDate")          Instant toDate,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorEntryId")   UUID    cursorEntryId,
            @Param("limit")           int     limit);

    /**
     * Sums the signed amounts of all entries for an account after a given instant.
     * Used to anchor the running balance for cursor-paginated statements.
     */
    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0)
            FROM ledger.ledger_entries
            WHERE account_id      = :accountId
              AND created_at      > :afterTimestamp
            """, nativeQuery = true)
    long sumSignedAmountsAfter(@Param("accountId")      UUID    accountId,
                                @Param("afterTimestamp") Instant afterTimestamp);

    /**
     * Fetches all entries for a ledger transaction, joining with the account
     * table to include account_type for display purposes.
     *
     * <p>Columns (index-based): 0=account_id, 1=direction, 2=amount,
     * 3=account_type, 4=narrative.
     */
    @Query(value = """
            SELECT
                e.account_id,
                e.direction,
                e.amount,
                a.account_type,
                e.narrative
            FROM ledger.ledger_entries e
            JOIN ledger.ledger_accounts a ON a.id = e.account_id
            WHERE e.ledger_transaction_id = :ledgerTransactionId
            ORDER BY e.direction
            """, nativeQuery = true)
    List<Object[]> findEntriesWithAccountType(
            @Param("ledgerTransactionId") UUID ledgerTransactionId);

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
     * Counts POSTED ledger transactions in a time window.
     * Used by the integrity job to report how many transactions were verified.
     */
    @Query(value = """
            SELECT COUNT(DISTINCT lt.id)
            FROM ledger.ledger_transactions lt
            WHERE lt.status    = 'POSTED'
              AND lt.created_at >= :from
              AND lt.created_at <  :to
            """, nativeQuery = true)
    long countPostedTransactionsInWindow(@Param("from") Instant from,
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
