package com.stash.kyc.submission.service;

import com.stash.kyc.submission.api.dto.SubmissionStatusResponse;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only status polling for KYC submissions. No state transitions
 * happen here — this is purely a projection of existing data.
 *
 * <p>Per the Module Boundaries doc §3.4: "while a submission is being
 * reviewed, the mobile app polls /kyc/submissions/{id} every few
 * seconds to update the UI. This is just repeated synchronous calls."
 *
 * <p><strong>Ownership enforcement:</strong> a user can only see their
 * own submission. Rather than returning 403 (which would confirm "this
 * submission exists but isn't yours" — an information leak), a mismatch
 * returns 404, identical to "this submission doesn't exist." Same
 * principle as User entity lookups elsewhere in the codebase (e.g.
 * UserRepository's deleted_at filtering returning empty rather than
 * a distinguishable error).
 */
@Service
public class KycSubmissionStatusService {

    private final KycSubmissionRepository submissionRepository;

    public KycSubmissionStatusService(KycSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    /**
     * Fetches a specific submission by ID, scoped to the requesting user.
     *
     * @param submissionId   the submission to fetch
     * @param requestingUserId the authenticated caller's user ID
     * @throws StashApiException 404 if not found OR if it belongs to a different user
     */
    @Transactional(readOnly = true)
    public SubmissionStatusResponse getStatus(UUID submissionId, UUID requestingUserId) {
        KycSubmission submission = submissionRepository.findById(submissionId)
                .filter(s -> s.getUserId().equals(requestingUserId))
                .orElseThrow(this::notFound);

        return toResponse(submission);
    }

    /**
     * Fetches the requesting user's most recent submission.
     *
     * @param requestingUserId the authenticated caller's user ID
     * @throws StashApiException 404 if the user has no submission at all
     */
    @Transactional(readOnly = true)
    public SubmissionStatusResponse getMyStatus(UUID requestingUserId) {
        KycSubmission submission = submissionRepository.findMostRecentByUserId(requestingUserId)
                .orElseThrow(this::notFound);

        return toResponse(submission);
    }

    private SubmissionStatusResponse toResponse(KycSubmission submission) {
        java.time.Instant updatedAt = submission.getDecidedAt() != null
                ? submission.getDecidedAt()
                : submission.getSubmittedAt();

        String rejectionReason = KycSubmission.DECISION_REJECTED.equals(submission.getDecision())
                ? submission.getDecisionReason()
                : null;

        return new SubmissionStatusResponse(
                submission.getId(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                updatedAt,
                rejectionReason
        );
    }

    private StashApiException notFound() {
        return new StashApiException(
                ErrorCode.KYC_SUBMISSION_NOT_FOUND,
                "No KYC submission found.",
                HttpStatus.NOT_FOUND
        );
    }
}
