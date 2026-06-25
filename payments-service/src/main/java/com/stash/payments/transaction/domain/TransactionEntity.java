package com.stash.payments.transaction.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "transaction")
public class TransactionEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "reference",              nullable = false, unique = true, length = 50)
    private String reference;

    @Column(name = "transaction_type",       nullable = false, length = 50)
    private String transactionType;

    @Column(name = "initiating_user_id",     nullable = false)
    private UUID initiatingUserId;

    @Column(name = "counterparty_user_id")
    private UUID counterpartyUserId;

    @Column(name = "gross_amount",           nullable = false)
    private long grossAmount;

    @Column(name = "fee_amount",             nullable = false)
    private long feeAmount;

    @Column(name = "net_amount",             nullable = false)
    private long netAmount;

    @Column(name = "status",                 nullable = false, length = 50)
    private String status;

    @Column(name = "external_provider",      length = 50)
    private String externalProvider;

    @Column(name = "external_reference",     length = 255)
    private String externalReference;     // Paystack reference

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;     // NULL until webhook confirms

    @Column(name = "source_ledger_account_id")
    private UUID sourceLedgerAccountId;

    @Column(name = "correlation_id",         length = 255)
    private String correlationId;

    @Column(name = "idempotency_key",        length = 255)
    private String idempotencyKey;

    @Column(name = "created_at",             nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;          // NULL until terminal status

    protected TransactionEntity() {}

    // Factory: creates a PENDING deposit transaction
    public static TransactionEntity pendingDeposit(String reference,
                                                    UUID userId,
                                                    long grossAmount,
                                                    String correlationId,
                                                    String idempotencyKey,
                                                    Instant now) {
        TransactionEntity t = new TransactionEntity();
        t.reference         = reference;
        t.transactionType   = "DEPOSIT";
        t.initiatingUserId  = userId;
        t.grossAmount       = grossAmount;
        t.feeAmount         = 0L;
        t.netAmount         = grossAmount;
        t.status            = "PENDING";
        t.externalProvider  = "PAYSTACK";
        t.correlationId     = correlationId;
        t.idempotencyKey    = idempotencyKey;
        t.createdAt         = now;
        return t;
    }

    // Factory: creates a PENDING withdrawal transaction
    public static TransactionEntity pendingWithdrawal(String reference,
                                                       UUID userId,
                                                       UUID sourceLedgerAccountId,
                                                       long grossAmount,
                                                       String correlationId,
                                                       String idempotencyKey,
                                                       Instant now) {
        TransactionEntity t = new TransactionEntity();
        t.reference                = reference;
        t.transactionType          = "WITHDRAWAL";
        t.initiatingUserId         = userId;
        t.sourceLedgerAccountId    = sourceLedgerAccountId;
        t.grossAmount              = grossAmount;
        t.feeAmount                = 0L;
        t.netAmount                = grossAmount;
        t.status                   = "PENDING";
        t.externalProvider         = "PAYSTACK";
        t.correlationId            = correlationId;
        t.idempotencyKey           = idempotencyKey;
        t.createdAt                = now;
        return t;
    }

    // Called by the webhook handler when Paystack confirms
    public void markCompleted(UUID ledgerTransactionId, Instant now) {
        this.status               = "COMPLETED";
        this.ledgerTransactionId  = ledgerTransactionId;
        this.completedAt          = now;
    }

    public void markFailed(Instant now) {
        this.status       = "FAILED";
        this.completedAt  = now;
    }

    public void setExternalReference(String ref) { this.externalReference = ref; }

    // Getters
    public UUID   getId()                  { return id; }
    public String getReference()           { return reference; }
    public String getTransactionType()     { return transactionType; }
    public UUID   getInitiatingUserId()    { return initiatingUserId; }
    public long   getGrossAmount()         { return grossAmount; }
    public long   getFeeAmount()           { return feeAmount; }
    public long   getNetAmount()           { return netAmount; }
    public String getStatus()              { return status; }
    public String getExternalReference()   { return externalReference; }
    public UUID   getLedgerTransactionId()    { return ledgerTransactionId; }
    public UUID   getSourceLedgerAccountId() { return sourceLedgerAccountId; }
    public String getCorrelationId()         { return correlationId; }
    public String getIdempotencyKey()      { return idempotencyKey; }
    public Instant getCreatedAt()          { return createdAt; }
}
