package com.stash.payments.paystack.repository;

import com.stash.payments.paystack.domain.PaystackSubaccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaystackSubaccountRepository extends JpaRepository<PaystackSubaccountEntity, UUID> {

    boolean existsByOwnerTypeAndOwnerId(String ownerType, UUID ownerId);

    Optional<PaystackSubaccountEntity> findByOwnerTypeAndOwnerId(
            String ownerType, UUID ownerId);

    Optional<PaystackSubaccountEntity> findByPaystackSubaccountCode(String code);
}
