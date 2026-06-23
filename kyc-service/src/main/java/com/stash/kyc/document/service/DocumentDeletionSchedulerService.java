package com.stash.kyc.document.service;

import com.stash.kyc.config.KycMessagingConfig;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import com.stash.kyc.submission.event.KycApprovedEvent;
import com.stash.kyc.submission.event.KycRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Schedules document deletion when a KYC submission reaches a terminal decision.
 *
 * <p><strong>Design decision — no separate scheduling table:</strong><br>
 * The issue text describes a {@code document_deletion_jobs} table with a
 * {@code scheduled_for} column. The canonical Schema doc §8.2 uses
 * {@code kyc.submission_documents.deletion_scheduled_at} as the scheduling
 * column, and §8.3 uses {@code kyc.kyc_deletion_attempts} (named
 * {@code document_deletion_jobs} in the v0.2-020 migration) as an
 * append-only audit trail of deletion *attempts* — not a scheduling table.
 * Adding a third scheduling mechanism on top of the two that already exist
 * in the schema would be wrong. This consumer uses the existing
 * {@code deletion_scheduled_at} column on {@code kyc.submission_documents}
 * as the spec intends.
 *
 * <p><strong>Idempotency:</strong> {@link KycSubmissionDocumentRepository#scheduleDeletionForSubmission}
 * uses a {@code WHERE deletion_status = 'RETAINED'} guard — documents already
 * scheduled (status = PENDING_DELETION) are not touched a second time.
 * Re-delivery of the same event updates zero rows and does nothing. No
 * separate deduplication table is needed because the state transition itself
 * is the idempotency guard.
 *
 * <p><strong>Grace windows per decision type:</strong>
 * <ul>
 *   <li>APPROVED: 24 hours — matches Schema doc §8.2 comment ("decided_at + 24h").</li>
 *   <li>REJECTED: 72 hours — rejection gives the user a longer appeal window before
 *       their documents are gone. This corrects the 24h-hardcoded value that was
 *       embedded inline in v0.2-023/024 before this consumer existed.</li>
 * </ul>
 *
 * <p><strong>Why "inline scheduling" was removed from v0.2-023/024:</strong>
 * Decision services should not have direct side effects on the documents table;
 * the event-driven approach here is the architecturally correct pattern per the
 * Module Boundaries doc §3.3 ("Deposit succeeds, notify user → Event").
 */
@Service
public class DocumentDeletionSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DocumentDeletionSchedulerService.class);

    private static final long APPROVED_GRACE_HOURS = 24L;
    private static final long REJECTED_GRACE_HOURS = 72L;

    private final KycSubmissionDocumentRepository documentRepository;

    public DocumentDeletionSchedulerService(KycSubmissionDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Listens for {@code kyc.approved} events and schedules document deletion
     * at 24 hours after the approval decision.
     */
    @RabbitListener(queues = KycMessagingConfig.DELETION_SCHEDULER_APPROVED_QUEUE)
    @Transactional
    public void onKycApproved(KycApprovedEvent event) {
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        scheduleDeletion(event.payload().submissionId(),
                occurredAt.plus(APPROVED_GRACE_HOURS, ChronoUnit.HOURS),
                "kyc.approved");
    }

    /**
     * Listens for {@code kyc.rejected} events and schedules document deletion
     * at 72 hours after the rejection decision (longer window for user appeal).
     */
    @RabbitListener(queues = KycMessagingConfig.DELETION_SCHEDULER_REJECTED_QUEUE)
    @Transactional
    public void onKycRejected(KycRejectedEvent event) {
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        scheduleDeletion(event.payload().submissionId(),
                occurredAt.plus(REJECTED_GRACE_HOURS, ChronoUnit.HOURS),
                "kyc.rejected");
    }

    /**
     * Exposed for direct unit-test invocation without a real broker.
     */
    @Transactional
    public int scheduleDeletion(UUID submissionId, Instant scheduledAt, String eventType) {
        int rowsUpdated = documentRepository.scheduleDeletionForSubmission(submissionId, scheduledAt);

        if (rowsUpdated == 0) {
            log.info("Deletion scheduling: no RETAINED documents found for submissionId={} " +
                    "(already scheduled or none exist) event={}", submissionId, eventType);
        } else {
            log.info("Deletion scheduling: {} document(s) scheduled for deletion " +
                    "at {} for submissionId={} event={}",
                    rowsUpdated, scheduledAt, submissionId, eventType);
        }

        return rowsUpdated;
    }
}
