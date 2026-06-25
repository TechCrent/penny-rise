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
}
