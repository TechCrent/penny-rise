package com.stash.kyc.document.domain;

import com.stash.shared.uuidv7.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Webhook idempotency record. See V1.3 migration. Mirrors the Payments
 * Service's webhook.processed_webhook_events pattern.
 */
@Entity
@Table(schema = "kyc", name = "processed_document_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedDocumentEvent {

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED    = "FAILED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider_event_id", nullable = false, unique = true, updatable = false)
    private String providerEventId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "processing_status", nullable = false, length = 50)
    private String processingStatus = STATUS_PENDING;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    public ProcessedDocumentEvent(String providerEventId, String correlationId) {
        this.id              = UuidV7Generator.generate();
        this.providerEventId = providerEventId;
        this.receivedAt      = Instant.now();
        this.correlationId   = correlationId;
    }

    public void markCompleted(UUID documentId) {
        this.documentId       = documentId;
        this.processingStatus = STATUS_COMPLETED;
        this.processedAt      = Instant.now();
    }
}
