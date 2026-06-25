package com.stash.payments.ledger.repository;

import com.stash.payments.ledger.domain.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Only LedgerService may use this repository — enforced by LedgerWriteArchitectureTest.
public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransactionEntity, UUID> {

    Optional<LedgerTransactionEntity> findByTransactionReference(String reference);
}
