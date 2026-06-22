package com.stash.kyc.submission.domain;

import com.stash.kyc.shared.crypto.GhanaCardEncryptionConverter;
import com.stash.shared.uuidv7.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for kyc.submissions. See Schema doc §8.1 (with the
 * PENDING_DOCUMENTS status addition from v0.2-021 — see V4 migration).
 *
 * <p>user_id is a LOGICAL reference to user_module.users.id in monolith-db.
 * No foreign key — different physical database. See Module Boundaries
 * doc Rule 4.
 */
@Entity
@Table(schema = "kyc", name = "submissions")
@Getter
@Setter
@NoArgsConstructor
public class KycSubmission {

    public static final String STATUS_PENDING_DOCUMENTS      = "PENDING_DOCUMENTS";
    public static final String STATUS_SUBMITTED              = "SUBMITTED";
    public static final String STATUS_REVIEWING              = "REVIEWING";
    public static final String STATUS_APPROVED               = "APPROVED";
    public static final String STATUS_REJECTED               = "REJECTED";
    public static final String STATUS_RESUBMISSION_REQUIRED  = "RESUBMISSION_REQUIRED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Convert(converter = GhanaCardEncryptionConverter.class)
    @Column(name = "ghana_card_number", nullable = false)
    private String ghanaCardNumber;

    @Column(name = "full_name_on_card", nullable = false, length = 255)
    private String fullNameOnCard;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "review_path", length = 50)
    private String reviewPath;

    @Column(name = "provider_decision", length = 50)
    private String providerDecision;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "reviewer_admin_id")
    private UUID reviewerAdminId;

    @Column(name = "decision", length = 50)
    private String decision;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    public KycSubmission(UUID userId, String ghanaCardNumber, String fullNameOnCard,
                         String correlationId) {
        this.id              = UuidV7Generator.generate();
        this.userId          = userId;
        this.ghanaCardNumber = ghanaCardNumber;
        this.fullNameOnCard  = fullNameOnCard;
        this.status          = STATUS_PENDING_DOCUMENTS;
        this.submittedAt     = Instant.now();
        this.correlationId   = correlationId;
    }

    public boolean isActive() {
        return STATUS_PENDING_DOCUMENTS.equals(status)
            || STATUS_SUBMITTED.equals(status)
            || STATUS_REVIEWING.equals(status)
            || STATUS_APPROVED.equals(status);
    }
}
