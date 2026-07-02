package com.stash.platform.user.repository;

import com.stash.platform.user.domain.DeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeletionRequestRepository extends JpaRepository<DeletionRequest, UUID> {

    @Query("""
            SELECT d FROM DeletionRequest d
            WHERE d.userId = :userId AND d.status = 'PENDING'
            """)
    Optional<DeletionRequest> findPendingByUserId(@Param("userId") UUID userId);

    /**
     * Claims due PENDING requests for the cleanup job using SELECT FOR UPDATE SKIP LOCKED.
     * Only rows whose scheduled_completion_at has passed are returned, in ascending
     * chronological order so the oldest requests are processed first.
     *
     * <p>SKIP LOCKED means concurrent job instances skip rows already claimed by another
     * worker rather than blocking, making the job safe to run on multiple instances.
     */
    @Query(value = """
            SELECT * FROM user_module.deletion_requests
            WHERE status = 'PENDING' AND scheduled_completion_at <= :now
            ORDER BY scheduled_completion_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<DeletionRequest> claimDueRequests(@Param("now") Instant now,
                                            @Param("batchSize") int batchSize);
}
