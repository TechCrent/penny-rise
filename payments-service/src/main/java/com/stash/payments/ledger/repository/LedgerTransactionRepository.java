package com.stash.payments.ledger.repository;

import com.stash.payments.ledger.domain.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Only LedgerService may use this repository — enforced by LedgerWriteArchitectureTest.
public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransactionEntity, UUID> {

    Optional<LedgerTransactionEntity> findByTransactionReference(String reference);

    /**
     * Batch-fetches business-reference/narrative enrichment for a set of
     * ledger transactions. Used by TransactionHistoryQueryService (via
     * LedgerService) to label history rows with their vault/susu-group
     * name instead of defaulting to "Wallet".
     *
     * <p>Columns (index-based): 0=id, 1=business_reference_type,
     * 2=business_reference_id, 3=narrative.
     */
    @Query(value = """
            SELECT id, business_reference_type, business_reference_id, narrative
            FROM ledger.ledger_transactions
            WHERE id IN :ids
            """, nativeQuery = true)
    List<Object[]> findBusinessReferencesByIds(@Param("ids") List<UUID> ids);
}
