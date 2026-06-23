package com.stash.kyc.document.service;

import com.stash.kyc.document.domain.KycSubmissionDocument;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled orchestrator for deferred KYC document deletion.
 *
 * <p>Runs every 15 minutes per this issue's acceptance criteria.
 * Selects documents due for deletion via {@code SELECT FOR UPDATE SKIP LOCKED}
 * — multiple instances of the KYC Service can run concurrently without
 * double-processing the same document.
 *
 * <p>Design decision — "job" is the document row, not a separate table:
 * {@code kyc.submission_documents} rows with {@code deletion_status IN
 * ('PENDING_DELETION', 'DELETE_FAILED') AND deletion_scheduled_at <= now()}
 * are the work queue. {@code kyc.document_deletion_jobs} (= Schema doc §8.3
 * {@code kyc_deletion_attempts}) is the append-only audit trail per attempt.
 * See {@link DocumentDeletionWorker} for the attempt logic.
 *
 * <p>Partial failure isolation: each document is processed in its own
 * nested transaction ({@code REQUIRES_NEW} in the worker). A failure on
 * document 2 of 3 does not roll back document 1's successful deletion.
 */
@Service
public class DocumentDeletionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentDeletionScheduler.class);

    private static final int BATCH_SIZE = 100;

    private final KycSubmissionDocumentRepository documentRepository;
    private final DocumentDeletionWorker worker;
    private final DocumentEscalationService escalationService;

    public DocumentDeletionScheduler(KycSubmissionDocumentRepository documentRepository,
                                     DocumentDeletionWorker worker,
                                     DocumentEscalationService escalationService) {
        this.documentRepository  = documentRepository;
        this.worker              = worker;
        this.escalationService   = escalationService;
    }

    /**
     * Main scheduled entry point. Runs every 15 minutes.
     */
    @Scheduled(cron = "0 0/15 * * * *")
    public void processDueDocuments() {
        List<KycSubmissionDocument> due = fetchDueDocuments();

        if (due.isEmpty()) {
            return;
        }

        log.info("Document deletion scheduler: processing {} due document(s)", due.size());

        int succeeded = 0;
        int failed    = 0;

        for (KycSubmissionDocument document : due) {
            KycSubmissionDocument current = documentRepository.findById(document.getId())
                    .orElseThrow();

            if (escalationService.requiresEscalation(current)) {
                escalationService.escalate(current);
                continue;
            }

            boolean success = worker.attemptDeletion(current);
            if (success) {
                succeeded++;
            } else {
                failed++;
                KycSubmissionDocument afterFailure = documentRepository.findById(document.getId())
                        .orElseThrow();
                if (escalationService.requiresEscalation(afterFailure)) {
                    escalationService.escalate(afterFailure);
                }
            }
        }

        log.info("Document deletion scheduler run complete: {} succeeded, {} failed",
                succeeded, failed);
    }

    /**
     * Short transaction for SKIP LOCKED selection — locks are released before
     * the worker runs so {@code REQUIRES_NEW} per-document transactions do not
     * deadlock against the outer batch transaction.
     */
    @Transactional
    protected List<KycSubmissionDocument> fetchDueDocuments() {
        return documentRepository.findDueForDeletion(BATCH_SIZE);
    }
}
