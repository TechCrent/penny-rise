package com.stash.payments.ledger.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions", schema = "ledger")
public class LedgerTransactionEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "transaction_type",        nullable = false, length = 50)
    private String transactionType;

    @Column(name = "business_reference_id")
    private UUID businessReferenceId;

    @Column(name = "business_reference_type", length = 50)
    private String businessReferenceType;

    @Column(name = "transaction_reference",   nullable = false, unique = true, length = 50)
    private String transactionReference;

    @Column(name = "amount",                  nullable = false)
    private long amount;

    @Column(name = "status",                  nullable = false, length = 50)
    private String status;

    @Column(name = "correlation_id",          length = 255)
    private String correlationId;

    @Column(name = "narrative",               length = 255)
    private String narrative;

    @Column(name = "created_at",              nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerTransactionEntity() {}

    public LedgerTransactionEntity(String transactionType, UUID businessReferenceId,
                            String businessReferenceType, String transactionReference,
                            long amount, String correlationId, String narrative,
                            Instant createdAt) {
        this.id                    = UUID.randomUUID();
        this.transactionType       = transactionType;
        this.businessReferenceId   = businessReferenceId;
        this.businessReferenceType = businessReferenceType;
        this.transactionReference  = transactionReference;
        this.amount                = amount;
        this.status                = "POSTED";
        this.correlationId         = correlationId;
        this.narrative             = narrative;
        this.createdAt             = createdAt;
    }

    public UUID   getId()                   { return id; }
    public String getTransactionReference() { return transactionReference; }
    public String getStatus()               { return status; }
    public long   getAmount()               { return amount; }
    public String getTransactionType()      { return transactionType; }
    public String getCorrelationId()        { return correlationId; }
}
