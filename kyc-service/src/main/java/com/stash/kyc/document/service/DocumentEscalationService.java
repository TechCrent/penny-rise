package com.stash.kyc.document.service;

import com.stash.kyc.document.domain.KycSubmissionDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles documents that have exhausted their deletion retry budget.
 *
 * <p>When {@link KycSubmissionDocument#getDeletionFailureCount()} reaches 5,
 * this service is called to:
 * <ol>
 *   <li>Log a structured P0 alert (picked up by the log aggregation
 *       infrastructure as an ops alert per the structured-logging
 *       conventions from v0.1-014).</li>
 *   <li>Leave the document in DELETE_FAILED — the row is NOT moved to DELETED
 *       because the bytes may still be in storage. Ops must investigate.</li>
 * </ol>
 *
 * <p>In v0.5, this will emit a RabbitMQ event to a dedicated ops-alert
 * queue that dispatches a Slack/PagerDuty notification. For v0.2, the
 * structured log at ERROR level with the P0 label is the alert mechanism.
 */
@Service
public class DocumentEscalationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentEscalationService.class);

    private static final int MAX_FAILURE_COUNT = 5;

    /**
     * Returns true if the document has reached the escalation threshold.
     */
    public boolean requiresEscalation(KycSubmissionDocument document) {
        return document.getDeletionFailureCount() >= MAX_FAILURE_COUNT;
    }

    /**
     * Records the P0 escalation. The document is left in DELETE_FAILED —
     * ops must investigate and manually delete from storage if bytes remain.
     */
    public void escalate(KycSubmissionDocument document) {
        log.error("[P0_ALERT] KYC document deletion failed after {} attempts. " +
                "Manual ops intervention required. " +
                "documentId={} submissionId={} storageProvider={} storageKey={}",
                document.getDeletionFailureCount(),
                document.getId(),
                document.getSubmissionId(),
                document.getStorageProvider(),
                document.getStorageKey()
        );
    }
}
