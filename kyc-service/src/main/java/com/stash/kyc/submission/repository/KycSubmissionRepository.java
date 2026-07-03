package com.stash.kyc.submission.repository;

import com.stash.kyc.submission.domain.KycSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /**
     * Finds the most recent submission for a user, regardless of status —
     * used by the /me polling endpoint. "Most recent" matters because a
     * user can have multiple submissions over time (initial, resubmission
     * after rejection); the mobile app always wants the latest one.
     */
    @Query("""
            SELECT s FROM KycSubmission s
            WHERE s.userId = :userId
            ORDER BY s.submittedAt DESC
            """)
    List<KycSubmission> findAllByUserIdOrderBySubmittedAtDesc(@Param("userId") UUID userId);

    default Optional<KycSubmission> findMostRecentByUserId(UUID userId) {
        List<KycSubmission> all = findAllByUserIdOrderBySubmittedAtDesc(userId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * v0.5-035: counts how many of a user's submissions have reached a
     * REJECTED decision — used to detect the second (or later) rejection
     * so the terminal-decision paths can tell the monolith to flag the
     * user for resubmission review instead of leaving them REJECTED.
     */
    long countByUserIdAndStatus(UUID userId, String status);
}
