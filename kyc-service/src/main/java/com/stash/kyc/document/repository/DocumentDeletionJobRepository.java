package com.stash.kyc.document.repository;

import com.stash.kyc.document.domain.DocumentDeletionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Append-only repository for deletion attempt audit trail. */
@Repository
public interface DocumentDeletionJobRepository extends JpaRepository<DocumentDeletionJob, UUID> {
}
