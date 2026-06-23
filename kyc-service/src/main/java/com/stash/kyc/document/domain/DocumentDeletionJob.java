package com.stash.kyc.document.domain;

import com.stash.shared.uuidv7.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for kyc.document_deletion_jobs — the append-only audit trail
 * of every deletion attempt per document, successful or failed.
 *
 * <p>Per Schema doc §8.3 (kyc_deletion_attempts): one row per ATTEMPT,
 * not per document. A document with 3 failed attempts and 1 success has
 * 4 rows here. This is intentional — the audit trail must prove every
 * attempt that was made, not just the final outcome.
 *
 * <p>This entity is NOT the "job queue" described in the issue text.
 * The job queue is {@code kyc.submission_documents.deletion_status} —
 * rows with status PENDING_DELETION or DELETE_FAILED are the "jobs".
 */
@Entity
@Table(schema = "kyc", name = "document_deletion_jobs")
@Getter
@Setter
@NoArgsConstructor
public class DocumentDeletionJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @Column(name = "outcome", nullable = false, length = 50)
    private String outcome;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_class", length = 255)
    private String errorClass;

    @Column(name = "storage_provider", nullable = false, length = 50)
    private String storageProvider;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    public DocumentDeletionJob(UUID documentId, String outcome, String errorMessage,
                                String errorClass, String storageProvider, String correlationId) {
        this.id              = UuidV7Generator.generate();
        this.documentId      = documentId;
        this.attemptedAt     = Instant.now();
        this.outcome         = outcome;
        this.errorMessage    = errorMessage;
        this.errorClass      = errorClass;
        this.storageProvider = storageProvider;
        this.correlationId   = correlationId;
    }
}
