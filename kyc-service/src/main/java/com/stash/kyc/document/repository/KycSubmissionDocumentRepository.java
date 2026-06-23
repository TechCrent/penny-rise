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

     * Schedules deletion for all RETAINED documents belonging to a submission.
     * Grace windows (24h approved, 72h rejected) are applied by
     * {@link com.stash.kyc.document.service.DocumentDeletionSchedulerService}.
     */

    @Modifying
    @Query("""
            UPDATE KycSubmissionDocument d
            SET d.deletionStatus = 'PENDING_DELETION',
                d.deletionScheduledAt = :scheduledAt
            WHERE d.submissionId = :submissionId
            AND d.deletionStatus = 'RETAINED'
            """)

    int scheduleDeletionForSubmission(@Param("submissionId") UUID submissionId,
                                      @Param("scheduledAt") Instant scheduledAt);

    /**
     * Selects documents due for deletion, locked against concurrent workers.
     * Uses native SQL for SELECT FOR UPDATE SKIP LOCKED — JPQL has no syntax for this.
     *
     * <p>Called inside a transaction — the lock is held until the transaction commits.
     */
    @Query(value = """
            SELECT * FROM kyc.submission_documents
            WHERE deletion_status IN ('PENDING_DELETION', 'DELETE_FAILED')
              AND deletion_scheduled_at <= NOW()
            ORDER BY deletion_scheduled_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<KycSubmissionDocument> findDueForDeletion(@Param("batchSize") int batchSize);

}
