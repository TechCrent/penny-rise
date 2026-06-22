package com.stash.kyc.submission.repository;

import com.stash.kyc.submission.domain.KycSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycSubmissionRepository extends JpaRepository<KycSubmission, UUID> {

    /**
     * Finds an active submission for a user — PENDING_DOCUMENTS, SUBMITTED,
     * REVIEWING, or APPROVED. Used to enforce the "one active submission
     * per user" rule.
     */
    @Query("""
            SELECT s FROM KycSubmission s
            WHERE s.userId = :userId
            AND s.status IN ('PENDING_DOCUMENTS', 'SUBMITTED', 'REVIEWING', 'APPROVED')
            ORDER BY s.submittedAt DESC
            """)
    Optional<KycSubmission> findActiveByUserId(@Param("userId") UUID userId);
}
