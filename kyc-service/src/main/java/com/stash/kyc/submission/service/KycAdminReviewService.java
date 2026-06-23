package com.stash.kyc.submission.service;

import com.stash.kyc.config.KycMessagingConfig;
import com.stash.kyc.document.domain.KycSubmissionDocument;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import com.stash.kyc.storage.ObjectStorage;
import com.stash.kyc.submission.api.dto.*;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.domain.ManualReviewQueueEntry;
import com.stash.kyc.submission.domain.ProviderDecisionRecord;
import com.stash.kyc.submission.event.KycApprovedEvent;
import com.stash.kyc.submission.event.KycRejectedEvent;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.kyc.submission.repository.ManualReviewQueueRepository;
import com.stash.kyc.submission.repository.ProviderDecisionRecordRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import com.stash.shared.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles admin manual review of escalated KYC submissions.
 *
 * <p>Queue source of truth is {@code kyc.manual_review_queue}, NOT a status
 * filter on kyc.submissions — a submission can be REVIEWING while still
 * mid-flight on the AUTO path, before any human escalation has happened.
 * Only rows actually inserted into manual_review_queue (by the AUTO-path
 * rejection handler in v0.2-023, or future routing logic) represent work
 * waiting for a human.
 *
 * <p>Endpoint naming reconciliation: System Design's public API table
 * specifies a single POST /admin/submissions/{id}/decide endpoint
 * (APPROVE or REJECT). This issue's acceptance criteria ask for separate
 * /approve and /reject endpoints. Both are exposed at the controller
 * layer; both delegate to the same {@link #decide} method here so there
 * is exactly one code path for the actual decision logic.
 */
@Service
public class KycAdminReviewService {

    private static final Logger log = LoggerFactory.getLogger(KycAdminReviewService.class);

    private static final Duration VIEW_URL_EXPIRY = Duration.ofMinutes(15);
    private static final int DELETION_GRACE_HOURS = 24;

    public static final String DECIDE_APPROVE = "APPROVE";
    public static final String DECIDE_REJECT  = "REJECT";

    private final KycSubmissionRepository submissionRepository;
    private final KycSubmissionDocumentRepository documentRepository;
    private final ManualReviewQueueRepository manualReviewQueueRepository;
    private final ProviderDecisionRecordRepository decisionRecordRepository;
    private final ObjectStorage objectStorage;
    private final RabbitTemplate rabbitTemplate;

    public KycAdminReviewService(KycSubmissionRepository submissionRepository,
                                 KycSubmissionDocumentRepository documentRepository,
                                 ManualReviewQueueRepository manualReviewQueueRepository,
                                 ProviderDecisionRecordRepository decisionRecordRepository,
                                 ObjectStorage objectStorage,
                                 RabbitTemplate rabbitTemplate) {
        this.submissionRepository        = submissionRepository;
        this.documentRepository          = documentRepository;
        this.manualReviewQueueRepository = manualReviewQueueRepository;
        this.decisionRecordRepository    = decisionRecordRepository;
        this.objectStorage               = objectStorage;
        this.rabbitTemplate              = rabbitTemplate;
    }

    // ── Queue listing ────────────────────────────────────────────────────

    /**
     * Returns the next page of submissions awaiting manual decision,
     * oldest first.
     *
     * @param cursor    opaque pagination cursor (null for first page)
     * @param pageSize  per System Design §14.4: default 20, max 100
     */
    @Transactional(readOnly = true)
    public AdminQueuePageResponse getQueue(String cursor, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int offset = cursor != null ? Integer.parseInt(cursor) : 0;

        List<ManualReviewQueueEntry> activeEntries = manualReviewQueueRepository.findAll().stream()
                .filter(e -> e.getRemovedFromQueueAt() == null)
                .sorted(Comparator.comparing(ManualReviewQueueEntry::getEnteredQueueAt))
                .skip(offset)
                .limit(size + 1L)
                .collect(Collectors.toList());

        boolean hasMore = activeEntries.size() > size;
        List<ManualReviewQueueEntry> pageEntries = hasMore
                ? activeEntries.subList(0, size)
                : activeEntries;

        List<AdminQueueItemResponse> items = pageEntries.stream()
                .map(this::toQueueItemResponse)
                .collect(Collectors.toList());

        String nextCursor = hasMore ? String.valueOf(offset + size) : null;

        return new AdminQueuePageResponse(items, nextCursor, hasMore);
    }

    private AdminQueueItemResponse toQueueItemResponse(ManualReviewQueueEntry entry) {
        KycSubmission submission = submissionRepository.findById(entry.getSubmissionId())
                .orElseThrow(() -> notFound(entry.getSubmissionId()));

        Map<String, String> viewUrls = buildDocumentViewUrls(submission.getId());

        return new AdminQueueItemResponse(
                submission.getId(),
                submission.getUserId(),
                submission.getFullNameOnCard(),
                submission.getSubmittedAt(),
                entry.getFlagReason(),
                viewUrls
        );
    }

    // ── Detail ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminSubmissionDetailResponse getDetail(UUID submissionId) {
        KycSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> notFound(submissionId));

        Map<String, String> viewUrls = buildDocumentViewUrls(submissionId);

        List<ProviderDecisionRecord> decisions = decisionRecordRepository.findAll().stream()
                .filter(d -> d.getSubmissionId().equals(submissionId))
                .sorted(Comparator.comparing(ProviderDecisionRecord::getRequestedAt))
                .toList();

        List<AdminSubmissionDetailResponse.ProviderDecisionSummary> decisionSummaries = decisions.stream()
                .map(d -> new AdminSubmissionDetailResponse.ProviderDecisionSummary(
                        d.getDecision(),
                        d.getConfidenceScore() != null ? d.getConfidenceScore().doubleValue() : null,
                        d.getRequestedAt()))
                .toList();

        return new AdminSubmissionDetailResponse(
                submission.getId(),
                submission.getUserId(),
                submission.getGhanaCardNumber(),
                submission.getFullNameOnCard(),
                submission.getDateOfBirth(),
                submission.getPhoneNumber(),
                submission.getStatus(),
                submission.getReviewPath(),
                submission.getSubmittedAt(),
                viewUrls,
                decisionSummaries
        );
    }

    // ── Decision ─────────────────────────────────────────────────────────

    /**
     * Decides a submission. Used by both the /approve and /reject endpoints.
     *
     * @param submissionId    the submission to decide
     * @param decisionType    {@link #DECIDE_APPROVE} or {@link #DECIDE_REJECT}
     * @param reason          required for REJECT, ignored for APPROVE
     * @param reviewerAdminId from the placeholder admin auth — see
     *                        {@link com.stash.kyc.shared.security.PlaceholderAdminAuthFilter}
     * @throws StashApiException 404 if not found, 422 if reject without reason
     */
    @Transactional
    public void decide(UUID submissionId, String decisionType, String reason, UUID reviewerAdminId) {
        KycSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> notFound(submissionId));

        if (DECIDE_REJECT.equals(decisionType) && (reason == null || reason.isBlank())) {
            throw new StashApiException(
                    ErrorCode.KYC_REJECTION_REASON_REQUIRED,
                    "A reason is required to reject a KYC submission.",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        manualReviewQueueRepository.findAll().stream()
                .filter(e -> e.getSubmissionId().equals(submissionId) && e.getRemovedFromQueueAt() == null)
                .findFirst()
                .ifPresent(entry -> {
                    entry.setRemovedFromQueueAt(Instant.now());
                    manualReviewQueueRepository.save(entry);
                });

        String correlationId = CorrelationContext.get();

        if (DECIDE_APPROVE.equals(decisionType)) {
            submission.finalizeManualDecision(KycSubmission.DECISION_APPROVED, null, reviewerAdminId);
            submissionRepository.save(submission);

            scheduleDeletion(submission);
            publishApprovedEvent(submission, correlationId);

            log.info("Submission APPROVED via MANUAL review submissionId={} reviewerAdminId={}",
                    submissionId, reviewerAdminId);

        } else {
            submission.finalizeManualDecision(KycSubmission.DECISION_REJECTED, reason, reviewerAdminId);
            submissionRepository.save(submission);

            scheduleDeletion(submission);
            publishRejectedEvent(submission, reason, correlationId);

            log.info("Submission REJECTED via MANUAL review submissionId={} reviewerAdminId={}",
                    submissionId, reviewerAdminId);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private void scheduleDeletion(KycSubmission submission) {
        Instant deletionScheduledAt = submission.getDecidedAt().plus(DELETION_GRACE_HOURS, ChronoUnit.HOURS);
        documentRepository.scheduleDeletionForSubmission(submission.getId(), deletionScheduledAt);
    }

    private void publishApprovedEvent(KycSubmission submission, String correlationId) {
        var event = new KycApprovedEvent(
                UUID.randomUUID().toString(), KycApprovedEvent.EVENT_TYPE,
                KycApprovedEvent.SCHEMA_VERSION, KycApprovedEvent.SOURCE_SERVICE,
                Instant.now(), correlationId,
                new KycApprovedEvent.Payload(submission.getId(), submission.getUserId(),
                        submission.getGhanaCardNumber())
        );
        rabbitTemplate.convertAndSend(KycMessagingConfig.KYC_EXCHANGE, "kyc.approved", event);
    }

    private void publishRejectedEvent(KycSubmission submission, String reason, String correlationId) {
        var event = new KycRejectedEvent(
                UUID.randomUUID().toString(), KycRejectedEvent.EVENT_TYPE,
                KycRejectedEvent.SCHEMA_VERSION, KycRejectedEvent.SOURCE_SERVICE,
                Instant.now(), correlationId,
                new KycRejectedEvent.Payload(submission.getId(), submission.getUserId(), reason)
        );
        rabbitTemplate.convertAndSend(KycMessagingConfig.KYC_EXCHANGE, "kyc.rejected", event);
    }

    private Map<String, String> buildDocumentViewUrls(UUID submissionId) {
        List<KycSubmissionDocument> documents = documentRepository.findBySubmissionId(submissionId);
        Map<String, String> urls = new LinkedHashMap<>();
        for (KycSubmissionDocument doc : documents) {
            urls.put(doc.getDocumentType(),
                    objectStorage.generateSignedDownloadUrl(doc.getStorageKey(), VIEW_URL_EXPIRY));
        }
        return urls;
    }

    private StashApiException notFound(UUID submissionId) {
        return new StashApiException(
                ErrorCode.KYC_SUBMISSION_NOT_FOUND,
                "KYC submission not found: " + submissionId,
                HttpStatus.NOT_FOUND
        );
    }
}
