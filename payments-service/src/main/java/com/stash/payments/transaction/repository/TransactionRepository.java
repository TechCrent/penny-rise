package com.stash.payments.transaction.repository;

import com.stash.payments.transaction.domain.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByReference(String reference);

    Optional<TransactionEntity> findByExternalReference(String paystackReference);

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * Counts PENDING transactions older than the given threshold.
     * Used by the integrity job to flag orphaned deposits and withdrawals.
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM transaction.transactions
            WHERE status     = 'PENDING'
              AND created_at < :threshold
            """, nativeQuery = true)
    long countStalePendingTransactions(@Param("threshold") Instant threshold);

    /**
     * Returns details of stale PENDING transactions for error logging.
     * Columns: 0=reference, 1=transaction_type, 2=created_at, 3=external_reference.
     */
    @Query(value = """
            SELECT reference, transaction_type, created_at, external_reference
            FROM transaction.transactions
            WHERE status     = 'PENDING'
              AND created_at < :threshold
            ORDER BY created_at ASC
            LIMIT 50
            """, nativeQuery = true)
    List<Object[]> findStalePendingTransactions(@Param("threshold") Instant threshold);
}
