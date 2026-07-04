package com.stash.kyc.submission.service;

import com.stash.kyc.config.KycMessagingConfig;
import com.stash.kyc.provider.GhanaCardProviderClient;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.domain.ManualReviewQueueEntry;
import com.stash.kyc.submission.domain.ProviderDecisionRecord;
import com.stash.kyc.submission.event.KycApprovedEvent;
import com.stash.kyc.submission.event.SubmissionReadyForReviewEvent;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.kyc.submission.repository.ManualReviewQueueRepository;
import com.stash.kyc.submission.repository.ProviderDecisionRecordRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@link SubmissionReadyForReviewEvent} and routes the submission
 * through the automated KYC provider, per Issue Plan §0.1's stub-provider
 * policy.
 *
 * <p><strong>Idempotency:</strong> if this consumer processes the same
 * event twice (RabbitMQ at-least-once delivery), the submission's status
 * check guards re-processing — once status is no longer REVIEWING (i.e.
 * already APPROVED or routed to manual review), {@link #handleSubmissionReadyForReview}
 * is a no-op for that submission.
 *
 * <p><strong>Retry:</strong> the provider call itself
 * ({@link #callProviderWithRetry}) retries up to 3 times with exponential
 * backoff on any exception, per this issue's acceptance criteria. This is
 * separate from RabbitMQ's own redelivery — a transient provider timeout
 * is retried inline before the message is acked, rather than relying on
 * the queue to redeliver.
 */

@Service
public class AutomatedDecisionService {

    private static final Logger log = LoggerFactory.getLogger(
        AutomatedDecisionService.class
    );
    private static final String PROVIDER_NAME = "stub-ghana-card-provider";
    private final KycSubmissionRepository submissionRepository;
    private final ProviderDecisionRecordRepository decisionRecordRepository;
    private final ManualReviewQueueRepository manualReviewQueueRepository;
    private final GhanaCardProviderClient providerClient;
    private final RabbitTemplate rabbitTemplate;

    public AutomatedDecisionService(
        KycSubmissionRepository submissionRepository,
        ProviderDecisionRecordRepository decisionRecordRepository,
        ManualReviewQueueRepository manualReviewQueueRepository,
        GhanaCardProviderClient providerClient,
        RabbitTemplate rabbitTemplate
    ) {
        this.submissionRepository = submissionRepository;
        this.decisionRecordRepository = decisionRecordRepository;
        this.manualReviewQueueRepository = manualReviewQueueRepository;
        this.providerClient = providerClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * RabbitMQ listener for the ready-for-review event.
     */

    @RabbitListener(queues = "kyc.provider.ready_for_review.queue")
    public void handleSubmissionReadyForReview(
        SubmissionReadyForReviewEvent event
    ) {
        processSubmission(
            event.payload().submissionId(),
            event.correlationId()
        );
    }

    /**
     * Processes a submission through the automated provider path.
     * Exposed as a separate method (not just the listener) so it can be
     * unit-tested directly without a real RabbitMQ broker.
     */

    @Transactional
    public void processSubmission(UUID submissionId, String correlationId) {
        KycSubmission submission = submissionRepository
            .findById(submissionId)
            .orElseThrow(() ->
                new StashApiException(
                    ErrorCode.KYC_SUBMISSION_NOT_FOUND,
                    "KYC submission not found: " + submissionId,
                    HttpStatus.NOT_FOUND
                )
            );

        // Idempotency guard: if already decided, this is a redelivered event — no-op.
        if (!KycSubmission.STATUS_REVIEWING.equals(submission.getStatus())) {
            log.info(
                "Submission already decided, skipping reprocessing submissionId={} status={}",
                submissionId,
                submission.getStatus()
            );
            return;
        }

        GhanaCardProviderClient.ProviderDecision decision;
        try {
            decision = callProviderWithRetry(submission);
        } catch (Exception e) {
            // After 3 retries exhausted: route to manual review as a safe fallback
            // rather than leaving the submission stuck. Per the Must Research doc:
            // "every submission must have a manual path... Automation is the
            // optimization, not the only path."
            log.error(
                "Provider unavailable after retries, routing to manual review submissionId={}: {}",
                submissionId,
                e.getMessage()
            );
            routeToManualReview(
                submission,
                "Provider unavailable after 3 retry attempts"
            );
            return;
        }

        // Record the decision — append-only, regardless of outcome
        ProviderDecisionRecord record = new ProviderDecisionRecord(
            submissionId,
            PROVIDER_NAME,
            decision.providerReference(),
            decision.decision(),
            decision.confidenceScore() == null
                ? null
                : BigDecimal.valueOf(decision.confidenceScore())
        );
        decisionRecordRepository.save(record);

        if (decision.isApproved()) {
            approveSubmission(submission, decision, correlationId);
        } else {
            rejectAndEscalate(submission, decision, correlationId);
        }
    }

    // ── Provider call with retry ───────────────────────────────────────────

    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    GhanaCardProviderClient.ProviderDecision callProviderWithRetry(
        KycSubmission submission
    ) {
        return providerClient.verify(
            submission.getId(),
            submission.getGhanaCardNumber(), // decrypted transparently by the JPA converter
            submission.getFullNameOnCard()
        );
    }

    @Recover
    GhanaCardProviderClient.ProviderDecision recoverFromProviderFailure(
        Exception e,
        KycSubmission submission
    ) {
        throw new StashApiException(
            ErrorCode.KYC_PROVIDER_UNAVAILABLE,
            "KYC provider unavailable after 3 attempts",
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    // ── Approval path ──────────────────────────────────────────────────────

    private void approveSubmission(
        KycSubmission submission,
        GhanaCardProviderClient.ProviderDecision decision,
        String correlationId
    ) {
        submission.setReviewPath(null); // AUTO path doesn't set review_path to MANUAL
        submission.finalizeDecision(
            KycSubmission.DECISION_APPROVED,
            decision.decision(),
            decision.providerReference(),
            "AUTO"
        );
        submissionRepository.save(submission);

        publishApprovedEvent(submission, correlationId);
        log.info(
            "Submission APPROVED via AUTO path submissionId={}",
            submission.getId()
        );
    }

    private void publishApprovedEvent(
        KycSubmission submission,
        String correlationId
    ) {
        var event = new KycApprovedEvent(
            UUID.randomUUID().toString(),
            KycApprovedEvent.EVENT_TYPE,
            KycApprovedEvent.SCHEMA_VERSION,
            KycApprovedEvent.SOURCE_SERVICE,
            Instant.now(),
            correlationId,
            new KycApprovedEvent.Payload(
                submission.getId(),
                submission.getUserId(),
                submission.getGhanaCardNumber()
            )
        );

        rabbitTemplate.convertAndSend(
            KycMessagingConfig.KYC_EXCHANGE,
            "kyc.approved",
            event
        );
    }

    // ── Rejection path ─────────────────────────────────────────────────────

    private void rejectAndEscalate(
        KycSubmission submission,
        GhanaCardProviderClient.ProviderDecision decision,
        String correlationId
    ) {
        submission.setStatus(KycSubmission.STATUS_SUBMITTED);
        submission.setReviewPath("MANUAL");
        submission.setProviderDecision(decision.decision());
        submission.setProviderReference(decision.providerReference());
        submission.setDecisionReason("Automated verification failed.");
        submissionRepository.save(submission);

        ManualReviewQueueEntry queueEntry = new ManualReviewQueueEntry(
            submission.getId(),
            "Automated rejection — flagged for human review"
        );
        manualReviewQueueRepository.save(queueEntry);

        log.info(
            "Submission routed to MANUAL review after automated failure submissionId={} correlationId={}",
            submission.getId(),
            correlationId
        );
    }

    // ── Provider-unavailable fallback ──────────────────────────────────────

    private void routeToManualReview(
        KycSubmission submission,
        String flagReason
    ) {
        submission.setStatus(KycSubmission.STATUS_SUBMITTED);
        submission.setReviewPath("MANUAL");
        submission.setDecisionReason(flagReason);
        submissionRepository.save(submission);
        ManualReviewQueueEntry queueEntry = new ManualReviewQueueEntry(
            submission.getId(),
            flagReason
        );
        manualReviewQueueRepository.save(queueEntry);
    }
}
