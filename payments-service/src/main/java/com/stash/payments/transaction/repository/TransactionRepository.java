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

    /**
     * Cursor-paginated transaction history for a user — either side of the
     * transaction (initiator or counterparty). {@code :cursorCreatedAt} is
     * exclusive (strictly older than the last-seen row); pass {@code null}
     * for the first page. Optional {@code :type}/{@code :fromDate}/
     * {@code :toDate} filters pass {@code null} to skip.
     */
    @Query(value = """
            SELECT id, reference, transaction_type, initiating_user_id, counterparty_user_id,
                   gross_amount, fee_amount, net_amount, status, ledger_transaction_id, created_at
            FROM transaction.transactions
            WHERE (initiating_user_id = :userId OR counterparty_user_id = :userId)
              AND (:type IS NULL OR transaction_type = :type)
              AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at >= :fromDate)
              AND (CAST(:toDate AS timestamptz) IS NULL OR created_at <= :toDate)
              AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL OR created_at < :cursorCreatedAt)
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findHistoryForUser(@Param("userId") UUID userId,
                                       @Param("type") String type,
                                       @Param("fromDate") Instant fromDate,
                                       @Param("toDate") Instant toDate,
                                       @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                       @Param("limit") int limit);
}
