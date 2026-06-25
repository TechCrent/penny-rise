package com.stash.payments.webhook.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_webhook_events", schema = "webhook")
public class ProcessedWebhookEventEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "provider",              nullable = false, length = 50)
    private String provider;

    @Column(name = "event_id",              nullable = false, length = 255)
    private String eventId;

    @Column(name = "event_type",            nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload_hash",          nullable = false, length = 255)
    private String payloadHash;

    @Column(name = "signature_valid",       nullable = false)
    private boolean signatureValid;

    @Column(name = "processing_status",     nullable = false, length = 50)
    private String processingStatus;

    @Column(name = "resulting_transaction_id")
    private UUID resultingTransactionId;

    @Column(name = "correlation_id",        length = 255)
    private String correlationId;

    @Column(name = "received_at",           nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected ProcessedWebhookEventEntity() {}

    public ProcessedWebhookEventEntity(String provider, String eventId, String eventType,
                                       String payloadHash, boolean signatureValid,
                                       String correlationId, Instant receivedAt) {
        this.id               = UUID.randomUUID();
        this.provider         = provider;
        this.eventId          = eventId;
        this.eventType        = eventType;
        this.payloadHash      = payloadHash;
        this.signatureValid   = signatureValid;
        this.correlationId    = correlationId;
        this.receivedAt       = receivedAt;
        this.processingStatus = signatureValid ? "PENDING" : "IGNORED";
        this.processedAt      = signatureValid ? null : receivedAt;
    }

    public void markCompleted(UUID transactionId, Instant now) {
        this.processingStatus       = "COMPLETED";
        this.resultingTransactionId = transactionId;
        this.processedAt            = now;
    }

    public void markFailed(Instant now) {
        this.processingStatus = "FAILED";
        this.processedAt      = now;
    }

    public void markIgnored(Instant now) {
        this.processingStatus = "IGNORED";
        this.processedAt      = now;
    }

    public UUID   getId()                     { return id; }
    public String getProvider()               { return provider; }
    public String getEventId()                { return eventId; }
    public String getEventType()              { return eventType; }
    public boolean isSignatureValid()         { return signatureValid; }
    public String getProcessingStatus()       { return processingStatus; }
    public UUID   getResultingTransactionId() { return resultingTransactionId; }
}
