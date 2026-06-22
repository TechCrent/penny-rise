package com.stash.kyc.document.repository;

import com.stash.kyc.document.domain.ProcessedDocumentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedDocumentEventRepository extends JpaRepository<ProcessedDocumentEvent, UUID> {

    Optional<ProcessedDocumentEvent> findByProviderEventId(String providerEventId);
}
