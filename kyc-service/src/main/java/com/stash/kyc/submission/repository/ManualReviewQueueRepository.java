package com.stash.kyc.submission.repository;

import com.stash.kyc.submission.domain.ManualReviewQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ManualReviewQueueRepository extends JpaRepository<ManualReviewQueueEntry, UUID> {

    long countByRemovedFromQueueAtIsNull();
}