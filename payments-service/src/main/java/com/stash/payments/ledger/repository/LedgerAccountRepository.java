package com.stash.payments.ledger.repository;

import com.stash.payments.ledger.domain.LedgerAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccountEntity, UUID> {

    /**
     * Idempotency check: find an existing account by owner and type.
     * Used by the provisioning service to skip duplicate creation.
     */
    Optional<LedgerAccountEntity> findByOwnerTypeAndOwnerIdAndAccountType(
            String ownerType, UUID ownerId, String accountType);
}
