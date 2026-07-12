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
     *
     * <p>{@code :scope} ("vault" / "wallet" / {@code null}) narrows to a
     * single account's activity, used by Home's Savings/Wallet states.
     * DEPOSIT/WITHDRAWAL scope is resolved from the ledger account actually
     * involved (destination for deposits, source for withdrawals) — this
     * works even while a transaction is still PENDING, since that column is
     * stamped at initiation time, unlike {@code ledger_transaction_id}.
     * TRANSFER/SUSU_CONTRIBUTION/SUSU_DISBURSEMENT don't stamp a
     * destination/source ledger account on this row (they resolve accounts
     * via ledger_entries instead) and are always wallet scope — vault
     * self-transfers are a Deposit/Withdrawal-shaped business reference,
     * not a distinct transaction_type, and are out of scope for this filter.
     */
    @Query(value = """
            SELECT t.id, t.reference, t.transaction_type, t.initiating_user_id, t.counterparty_user_id,
                   t.gross_amount, t.fee_amount, t.net_amount, t.status, t.ledger_transaction_id, t.created_at
            FROM transaction.transactions t
            LEFT JOIN ledger.ledger_accounts la_dest ON la_dest.id = t.destination_ledger_account_id
            LEFT JOIN ledger.ledger_accounts la_src  ON la_src.id  = t.source_ledger_account_id
            WHERE (t.initiating_user_id = :userId OR t.counterparty_user_id = :userId)
              AND (:type IS NULL OR t.transaction_type = :type)
              AND (CAST(:fromDate AS timestamptz) IS NULL OR t.created_at >= :fromDate)
              AND (CAST(:toDate AS timestamptz) IS NULL OR t.created_at <= :toDate)
              AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL OR t.created_at < :cursorCreatedAt)
              AND (
                    :scope IS NULL
                    OR (:scope = 'vault' AND (la_dest.account_type = 'VAULT' OR la_src.account_type = 'VAULT'))
                    OR (:scope = 'wallet' AND (
                          (la_dest.account_type = 'USER_WALLET' OR la_src.account_type = 'USER_WALLET')
                          OR t.transaction_type IN ('TRANSFER', 'SUSU_CONTRIBUTION', 'SUSU_DISBURSEMENT')
                        ))
                  )
            ORDER BY t.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findHistoryForUser(@Param("userId") UUID userId,
                                       @Param("type") String type,
                                       @Param("fromDate") Instant fromDate,
                                       @Param("toDate") Instant toDate,
                                       @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                       @Param("scope") String scope,
                                       @Param("limit") int limit);

    /**
     * References of PENDING deposits older than the threshold that have a
     * Paystack reference (i.e. the charge was actually initiated, just never
     * confirmed) — candidates for verify-based reconciliation when a webhook
     * is missed or delayed. Used by DepositReconciliationJob.
     */
    @Query(value = """
            SELECT reference
            FROM transaction.transactions
            WHERE status              = 'PENDING'
              AND transaction_type    = 'DEPOSIT'
              AND external_reference IS NOT NULL
              AND created_at          < :threshold
            ORDER BY created_at ASC
            LIMIT 50
            """, nativeQuery = true)
    List<String> findStalePendingDepositReferences(@Param("threshold") Instant threshold);
}
