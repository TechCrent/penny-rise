package com.stash.kyc.submission.service;

import com.stash.kyc.storage.ObjectStorage;
import com.stash.kyc.submission.api.dto.CreateSubmissionRequest;
import com.stash.kyc.submission.api.dto.CreateSubmissionResponse;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import com.stash.shared.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles KYC submission creation — the first step of the KYC flow.
 *
 * <p>Document uploads are confirmed separately (v0.2-022); this service
 * only creates the submission record and issues signed upload URLs.
 *
 * <p><strong>PII invariants:</strong>
 * <ul>
 *   <li>Raw Ghana Card number is NEVER logged.</li>
 *   <li>The entity stores it encrypted via {@code GhanaCardEncryptionConverter} —
 *       JPA handles encryption/decryption transparently at the persistence
 *       boundary, so this service never sees ciphertext directly, but it
 *       also never logs the plaintext it does see.</li>
 * </ul>
 */
@Service
public class KycSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(KycSubmissionService.class);

    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(15);

    private final KycSubmissionRepository submissionRepository;
    private final ObjectStorage objectStorage;

    public KycSubmissionService(KycSubmissionRepository submissionRepository,
                                ObjectStorage objectStorage) {
        this.submissionRepository = submissionRepository;
        this.objectStorage        = objectStorage;
    }

    /**
     * Creates a new KYC submission for the given user.
     *
     * @param userId  the authenticated user's ID (from the JWT, forwarded
     *                by the monolith's proxy layer — see api/user/ controller)
     * @param request validated Ghana Card number + full name
     * @return the created submission's ID, status, and signed upload URLs
     * @throws StashApiException 409 if the user already has an active submission
     */
    @Transactional
    public CreateSubmissionResponse createSubmission(UUID userId, CreateSubmissionRequest request) {
        submissionRepository.findActiveByUserId(userId).ifPresent(existing -> {
            log.debug("Submission rejected: active submission already exists userId={} status={}",
                    userId, existing.getStatus());
            throw new StashApiException(
                    ErrorCode.KYC_SUBMISSION_ALREADY_ACTIVE,
                    "You already have an active KYC submission.",
                    HttpStatus.CONFLICT
            );
        });

        String correlationId = CorrelationContext.get();

        KycSubmission submission = new KycSubmission(
                userId,
                request.ghanaCardNumber(),
                request.fullName(),
                correlationId
        );
        submissionRepository.save(submission);

        Map<String, String> uploadUrls = generateUploadUrls(submission.getId());

        log.info("KYC submission created userId={} submissionId={}", userId, submission.getId());

        return new CreateSubmissionResponse(
                submission.getId(),
                submission.getStatus(),
                uploadUrls
        );
    }

    private Map<String, String> generateUploadUrls(UUID submissionId) {
        Map<String, String> urls = new LinkedHashMap<>();

        urls.put(CreateSubmissionResponse.DOC_FRONT_OF_CARD,
                objectStorage.generateSignedUploadUrl(
                        documentKey(submissionId, "front-of-card"),
                        "image/jpeg", UPLOAD_URL_EXPIRY));

        urls.put(CreateSubmissionResponse.DOC_BACK_OF_CARD,
                objectStorage.generateSignedUploadUrl(
                        documentKey(submissionId, "back-of-card"),
                        "image/jpeg", UPLOAD_URL_EXPIRY));

        urls.put(CreateSubmissionResponse.DOC_SELFIE,
                objectStorage.generateSignedUploadUrl(
                        documentKey(submissionId, "selfie"),
                        "image/jpeg", UPLOAD_URL_EXPIRY));

        return urls;
    }

    private String documentKey(UUID submissionId, String documentName) {
        return "submissions/" + submissionId + "/" + documentName + ".jpg";
    }
}
