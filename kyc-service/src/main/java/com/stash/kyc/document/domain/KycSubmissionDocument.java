package com.stash.kyc.document.domain;

import com.stash.shared.uuidv7.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for kyc.submission_documents. See Schema doc §8.2
 * (kyc_documents) and v0.2-020's migration.
 */
@Entity
@Table(schema = "kyc", name = "submission_documents")
@Getter
@Setter
@NoArgsConstructor
public class KycSubmissionDocument {

    public static final String TYPE_FRONT_OF_CARD = "FRONT_OF_CARD";
    public static final String TYPE_BACK_OF_CARD  = "BACK_OF_CARD";
    public static final String TYPE_SELFIE        = "SELFIE";

    public static final String DELETION_STATUS_RETAINED = "RETAINED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "storage_provider", nullable = false, length = 50)
    private String storageProvider;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "sha256_hash", nullable = false, length = 255)
    private String sha256Hash;

    @Column(name = "deletion_status", nullable = false, length = 50)
    private String deletionStatus = DELETION_STATUS_RETAINED;

    @Column(name = "deletion_scheduled_at")
    private Instant deletionScheduledAt;

    @Column(name = "deletion_completed_at")
    private Instant deletionCompletedAt;

    @Column(name = "deletion_failure_count", nullable = false)
    private Integer deletionFailureCount = 0;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public KycSubmissionDocument(UUID submissionId, String documentType, String storageProvider,
                                 String storageKey, String contentType, Long sizeBytes,
                                 String sha256Hash) {
        this.id              = UuidV7Generator.generate();
        this.submissionId    = submissionId;
        this.documentType    = documentType;
        this.storageProvider = storageProvider;
        this.storageKey      = storageKey;
        this.contentType     = contentType;
        this.sizeBytes       = sizeBytes;
        this.sha256Hash      = sha256Hash;
        this.uploadedAt      = Instant.now();
    }
}
