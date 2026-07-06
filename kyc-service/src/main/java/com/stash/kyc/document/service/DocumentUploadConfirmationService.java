package com.stash.kyc.document.service;

import com.stash.kyc.document.api.dto.DocumentUploadConfirmationRequest;
import com.stash.kyc.document.api.dto.DocumentUploadConfirmationResponse;
import com.stash.kyc.document.domain.KycSubmissionDocument;
import com.stash.kyc.document.domain.ProcessedDocumentEvent;
import com.stash.kyc.document.repository.KycSubmissionDocumentRepository;
import com.stash.kyc.document.repository.ProcessedDocumentEventRepository;
import com.stash.kyc.submission.domain.KycSubmission;
import com.stash.kyc.submission.event.SubmissionReadyForReviewApplicationEvent;
import com.stash.kyc.submission.repository.KycSubmissionRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import com.stash.shared.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Confirms a document upload and, when all three required documents are
 * present, transitions the submission from PENDING_DOCUMENTS to REVIEWING.
 */
@Service
public class DocumentUploadConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadConfirmationService.class);

    private static final Set<String> REQUIRED_DOCUMENT_TYPES = Set.of(
            KycSubmissionDocument.TYPE_FRONT_OF_CARD,
            KycSubmissionDocument.TYPE_BACK_OF_CARD,
            KycSubmissionDocument.TYPE_SELFIE
    );

    private final KycSubmissionRepository submissionRepository;
    private final KycSubmissionDocumentRepository documentRepository;
    private final ProcessedDocumentEventRepository processedEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final String storageProviderName;

    public DocumentUploadConfirmationService(
            KycSubmissionRepository submissionRepository,
            KycSubmissionDocumentRepository documentRepository,
            ProcessedDocumentEventRepository processedEventRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${stash.kyc.storage.provider:local}") String storageProvider) {
        this.submissionRepository     = submissionRepository;
        this.documentRepository       = documentRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher           = eventPublisher;
        this.storageProviderName      = storageProvider.equalsIgnoreCase("supabase") ? "SUPABASE" : "LOCAL";
    }

    @Transactional
    public DocumentUploadConfirmationResponse confirmUpload(
            UUID submissionId, DocumentUploadConfirmationRequest request) {

        var existingEvent = processedEventRepository.findByProviderEventId(request.providerEventId());
        if (existingEvent.isPresent() && ProcessedDocumentEvent.STATUS_COMPLETED
                .equals(existingEvent.get().getProcessingStatus())) {
            log.info("Duplicate document event ignored providerEventId={}", request.providerEventId());

            KycSubmission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> notFound(submissionId));

            return new DocumentUploadConfirmationResponse(
                    existingEvent.get().getDocumentId(), submission.getStatus());
        }

        String correlationId = CorrelationContext.get();
        ProcessedDocumentEvent event = new ProcessedDocumentEvent(
                request.providerEventId(), correlationId);

        try {
            processedEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent duplicate document event detected providerEventId={}",
                    request.providerEventId());
            KycSubmission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> notFound(submissionId));
            return new DocumentUploadConfirmationResponse(null, submission.getStatus());
        }

        KycSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> notFound(submissionId));

        KycSubmissionDocument document = documentRepository
                .findBySubmissionIdAndDocumentType(submissionId, request.documentType())
                .orElse(null);

        if (document == null) {
            document = new KycSubmissionDocument(
                    submissionId,
                    request.documentType(),
                    storageProviderName,
                    request.storageKey(),
                    request.contentType(),
                    request.sizeBytes(),
                    request.sha256Hash()
            );
        } else {
            document.setStorageKey(request.storageKey());
            document.setContentType(request.contentType());
            document.setSizeBytes(request.sizeBytes());
            document.setSha256Hash(request.sha256Hash());
            document.setUploadedAt(Instant.now());
        }
        documentRepository.save(document);

        event.markCompleted(document.getId());
        processedEventRepository.save(event);

        if (KycSubmission.STATUS_PENDING_DOCUMENTS.equals(submission.getStatus())) {
            List<KycSubmissionDocument> allDocuments =
                    documentRepository.findBySubmissionId(submissionId);

            Set<String> uploadedTypes = allDocuments.stream()
                    .map(KycSubmissionDocument::getDocumentType)
                    .collect(Collectors.toSet());

            if (uploadedTypes.containsAll(REQUIRED_DOCUMENT_TYPES)) {
                submission.setStatus(KycSubmission.STATUS_REVIEWING);
                submissionRepository.save(submission);

                eventPublisher.publishEvent(new SubmissionReadyForReviewApplicationEvent(
                        this, submission.getId(), submission.getUserId(), correlationId));

                log.info("Submission transitioned to REVIEWING submissionId={}", submissionId);
            }
        }

        return new DocumentUploadConfirmationResponse(document.getId(), submission.getStatus());
    }

    private StashApiException notFound(UUID submissionId) {
        return new StashApiException(
                ErrorCode.KYC_SUBMISSION_NOT_FOUND,
                "KYC submission not found: " + submissionId,
                HttpStatus.NOT_FOUND
        );
    }
}
