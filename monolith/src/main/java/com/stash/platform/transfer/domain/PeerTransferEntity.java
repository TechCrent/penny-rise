package com.stash.platform.transfer.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "peer_transfers", schema = "transfer")
public class PeerTransferEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "sender_user_id",             nullable = false) private UUID    senderUserId;
    @Column(name = "recipient_user_id",          nullable = false) private UUID    recipientUserId;
    @Column(name = "amount",                     nullable = false) private long    amount;
    @Column(name = "fee_amount",                 nullable = false) private long    feeAmount;
    @Column(name = "status",                     nullable = false) private String  status;
    @Column(name = "note")                                         private String  note;
    @Column(name = "transaction_id")                               private UUID    transactionId;
    @Column(name = "idempotency_key",            nullable = false) private String  idempotencyKey;
    @Column(name = "counted_against_free_quota", nullable = false) private boolean countedAgainstFreeQuota;
    @Column(name = "completed_at")                                 private Instant completedAt;
    @Column(name = "created_at",                 nullable = false) private Instant createdAt;

    protected PeerTransferEntity() {}

    public static PeerTransferEntity create(UUID senderUserId, UUID recipientUserId,
                                             long amount, long feeAmount, String note,
                                             String idempotencyKey, Instant now) {
        PeerTransferEntity t = new PeerTransferEntity();
        t.senderUserId              = senderUserId;
        t.recipientUserId           = recipientUserId;
        t.amount                    = amount;
        t.feeAmount                 = feeAmount;
        t.status                    = "PENDING";
        t.note                      = note;
        t.idempotencyKey            = idempotencyKey;
        t.countedAgainstFreeQuota   = false;
        t.createdAt                 = now;
        return t;
    }

    public void complete(UUID transactionId, boolean freeQuota, Instant now) {
        this.status                   = "COMPLETED";
        this.transactionId            = transactionId;
        this.countedAgainstFreeQuota  = freeQuota;
        this.completedAt              = now;
    }

    public void fail(Instant now) {
        this.status      = "FAILED";
        this.completedAt = now;
    }

    public UUID    getId()                       { return id; }
    public UUID    getSenderUserId()             { return senderUserId; }
    public UUID    getRecipientUserId()          { return recipientUserId; }
    public long    getAmount()                   { return amount; }
    public long    getFeeAmount()                { return feeAmount; }
    public String  getStatus()                   { return status; }
    public String  getNote()                     { return note; }
    public UUID    getTransactionId()            { return transactionId; }
    public String  getIdempotencyKey()           { return idempotencyKey; }
    public boolean isCountedAgainstFreeQuota()   { return countedAgainstFreeQuota; }
    public Instant getCompletedAt()              { return completedAt; }
    public Instant getCreatedAt()                { return createdAt; }
}
