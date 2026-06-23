package com.stash.kyc.submission.domain;

import com.stash.shared.uuidv7.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for kyc.provider_decisions — append-only log of every
 * automated provider response. See v0.2-020's migration notes for the
 * Schema doc reconciliation flag on this table.
 */

@Entity
@Table(schema = "kyc", name = "provider_decisions")
@Getter
@Setter
@NoArgsConstructor

public class ProviderDecisionRecord {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Column(name = "provider_reference", nullable = false, length = 255)
    private String providerReference;

    @Column(name = "decision", nullable = false, length = 50)
    private String decision;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;
    public ProviderDecisionRecord(UUID submissionId, String providerName, String providerReference,
                                  String decision, BigDecimal confidenceScore) {
        this.id                  = UuidV7Generator.generate();
        this.submissionId        = submissionId;
        this.providerName        = providerName;
        this.providerReference   = providerReference;
        this.decision            = decision;
        this.confidenceScore     = confidenceScore;
        this.requestedAt         = Instant.now();
        this.respondedAt         = Instant.now();
    }
}