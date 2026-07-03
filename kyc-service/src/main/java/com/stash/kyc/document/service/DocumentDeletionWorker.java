package com.stash.kyc.document.service;

import com.stash.kyc.document.domain.DocumentDeletionJob;
import com.stash.kyc.document.domain.KycSubmissionDocument;
import com.stash.kyc.document.repository.DocumentDeletionJobRepository;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import com.stash.kyc.storage.ObjectStorage;
import com.stash.shared.correlation.CorrelationContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Performs the actual storage-provider call to delete a single KYC document.
 * Runs in its own transaction ({@link Propagation#REQUIRES_NEW}) so a failure
 * on one document does not roll back successful deletions of others in the
 * same scheduler batch. This is the "partial failure" isolation mechanism.
 *
 * <p>On success: sets deletion_status=DELETED, deletion_completed_at=now(),
 * nulls out storage_key (compliance — no storage path survives after deletion).
 *
 * <p>On failure: sets deletion_status=DELETE_FAILED, increments deletion_failure_count.
 * The document remains in the table as the audit record; only its bytes are gone.
 */
@Component
public class DocumentDeletionWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentDeletionWorker.class);

    private final ObjectStorage objectStorage;
    private final KycSubmissionDocumentRepository documentRepository;
    private final DocumentDeletionJobRepository deletionJobRepository;
    private final Counter deletionFailureCounter;

    public DocumentDeletionWorker(ObjectStorage objectStorage,
                                  KycSubmissionDocumentRepository documentRepository,
                                  DocumentDeletionJobRepository deletionJobRepository,
                                  MeterRegistry meterRegistry) {
        this.objectStorage         = objectStorage;
        this.documentRepository    = documentRepository;
        this.deletionJobRepository = deletionJobRepository;
        // v0.5-027: no metric existed anywhere in the deletion path before this
        // — the only prior signal was DocumentEscalationService's [P0_ALERT] log
        // line, fired once a single document hits 5 failures. This counter
        // tracks every failed attempt across all documents, so a rising trend
        // is visible before any individual document escalates. Renders as
        // kyc_document_deletion_failures_total on /actuator/prometheus.
        this.deletionFailureCounter = meterRegistry.counter("kyc.document.deletion.failures");
    }

    /**
     * Attempts to delete one document's bytes from storage.
     *
     * @param document the document to delete
     * @return true if deletion succeeded, false if it failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean attemptDeletion(KycSubmissionDocument document) {
        KycSubmissionDocument current = documentRepository.findById(document.getId())
                .orElseThrow();

        String storageKey = current.getStorageKey();

        try {
            objectStorage.delete(storageKey);

            current.setDeletionStatus("DELETED");
            current.setDeletionCompletedAt(Instant.now());
            current.setStorageKey(null);
            documentRepository.save(current);

            recordAttempt(current, "SUCCESS", null, null);

            log.info("Document deleted successfully documentId={} submissionId={}",
                    current.getId(), current.getSubmissionId());
            return true;

        } catch (Exception e) {
            current.setDeletionStatus("DELETE_FAILED");
            current.setDeletionFailureCount(current.getDeletionFailureCount() + 1);
            documentRepository.save(current);

            recordAttempt(current, "FAILURE", e.getMessage(), e.getClass().getName());
            deletionFailureCounter.increment();

            log.warn("Document deletion failed documentId={} attempt={} error={}",
                    current.getId(), current.getDeletionFailureCount(), e.getMessage());
            return false;
        }
    }

    private void recordAttempt(KycSubmissionDocument document, String outcome,
                                String errorMessage, String errorClass) {
        var attempt = new DocumentDeletionJob(
                document.getId(),
                outcome,
                errorMessage,
                errorClass,
                document.getStorageProvider(),
                CorrelationContext.get()
        );
        deletionJobRepository.save(attempt);
    }
}
