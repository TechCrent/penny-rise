package com.stash.platform.user.service;

import com.stash.platform.user.domain.DeletionRequest;
import com.stash.platform.user.repository.DeletionRequestRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles account deletion requests — submission and cancellation only.
 *
 * <p>The actual deletion execution (soft-deleting the user, revoking
 * sessions, closing vaults/ledger accounts) is NOT in scope here — that
 * ships in v0.5 as the cleanup job (see deletion_requests_cleanup_job_idx
 * partial index from v0.2-006, built exactly for that future job).
 *
 * <p>One active (PENDING) request per user is enforced — both at the
 * application layer here AND at the database layer via the
 * {@code deletion_requests_one_pending_per_user} EXCLUDE constraint
 * from the v0.2-006 migration, so even a race condition cannot create
 * two PENDING rows for the same user.
 */
@Service
public class DeletionRequestService {

    private static final Logger log = LoggerFactory.getLogger(DeletionRequestService.class);

    private final DeletionRequestRepository deletionRequestRepository;
    private final DeletionBlockerSnapshotProvider blockerSnapshotProvider;

    public DeletionRequestService(DeletionRequestRepository deletionRequestRepository,
                                  DeletionBlockerSnapshotProvider blockerSnapshotProvider) {
        this.deletionRequestRepository = deletionRequestRepository;
        this.blockerSnapshotProvider   = blockerSnapshotProvider;
    }

    /**
     * Submits a new deletion request for the authenticated user.
     *
     * @param userId the authenticated user's ID
     * @return the created request
     * @throws StashApiException 409 if a PENDING request already exists
     */
    @Transactional
    public DeletionRequest submit(UUID userId) {
        deletionRequestRepository.findPendingByUserId(userId).ifPresent(existing -> {
            log.debug("Deletion request rejected: pending request already exists userId={}", userId);
            throw new StashApiException(
                    ErrorCode.USER_DELETION_REQUEST_ALREADY_PENDING,
                    "You already have a pending account deletion request.",
                    HttpStatus.CONFLICT
            );
        });

        var blockers = blockerSnapshotProvider.buildSnapshot(userId);
        DeletionRequest request = new DeletionRequest(userId, blockers);
        deletionRequestRepository.save(request);

        log.info("Deletion request submitted userId={} scheduledCompletionAt={}",
                userId, request.getScheduledCompletionAt());

        return request;
    }

    /**
     * Cancels the authenticated user's pending deletion request.
     *
     * @param userId the authenticated user's ID
     * @throws StashApiException 404 if no PENDING request exists for this user
     */
    @Transactional
    public void cancel(UUID userId) {
        DeletionRequest request = deletionRequestRepository.findPendingByUserId(userId)
                .orElseThrow(() -> new StashApiException(
                        ErrorCode.NOT_FOUND,
                        "No pending deletion request found.",
                        HttpStatus.NOT_FOUND
                ));

        request.cancel();
        deletionRequestRepository.save(request);

        log.info("Deletion request cancelled userId={}", userId);
    }
}
