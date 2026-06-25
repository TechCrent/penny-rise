package com.stash.payments.transaction.repository;

import com.stash.payments.transaction.domain.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByReference(String reference);

    Optional<TransactionEntity> findByExternalReference(String paystackReference);

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);
}
