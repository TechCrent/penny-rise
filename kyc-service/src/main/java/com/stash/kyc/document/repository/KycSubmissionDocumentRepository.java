package com.stash.kyc.document.repository;

import com.stash.kyc.document.domain.KycSubmissionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycSubmissionDocumentRepository extends JpaRepository<KycSubmissionDocument, UUID> {

    List<KycSubmissionDocument> findBySubmissionId(UUID submissionId);

    @Query("""
            SELECT d FROM KycSubmissionDocument d
            WHERE d.submissionId = :submissionId AND d.documentType = :documentType
            """)
    Optional<KycSubmissionDocument> findBySubmissionIdAndDocumentType(
            @Param("submissionId") UUID submissionId,
            @Param("documentType") String documentType);

    /**

     * Schedules deletion for all documents belonging to a submission.
     * Called when a submission reaches a terminal decision (APPROVED/REJECTED).
     * Per Schema doc §8.2: deletion_scheduled_at = decided_at + 24h.
     */

    @Modifying
    @org.springframework.data.jpa.repository.Query("""
UPDATE KycSubmissionDocument d
SET d.deletionStatus = 'PENDING_DELETION',
d.deletionScheduledAt = :scheduledAt
WHERE d.submissionId = :submissionId
AND d.deletionStatus = 'RETAINED'
""")

    int scheduleDeletionForSubmission(@org.springframework.data.repository.query.Param("submissionId") java.util.UUID submissionId,
                                      @org.springframework.data.repository.query.Param("scheduledAt") Instant scheduledAt);

}
