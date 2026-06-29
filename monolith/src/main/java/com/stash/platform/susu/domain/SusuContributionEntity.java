package com.stash.platform.susu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "susu_contributions", schema = "susu")
public class SusuContributionEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "susu_round_id", nullable = false)
    private UUID susuRoundId;

    @Column(name = "susu_group_id", nullable = false)
    private UUID susuGroupId;

    @Column(name = "member_user_id", nullable = false)
    private UUID memberUserId;

    @Column(name = "expected_amount", nullable = false)
    private long expectedAmount;

    @Column(name = "collected_amount")
    private Long collectedAmount;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "collection_attempt_count", nullable = false)
    private int collectionAttemptCount;

    @Column(name = "penalty_amount", nullable = false)
    private long penaltyAmount;

    @Column(name = "is_late", nullable = false)
    private boolean isLate;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SusuContributionEntity() {}

    public UUID    getId()                     { return id; }
    public UUID    getSusuRoundId()            { return susuRoundId; }
    public UUID    getSusuGroupId()            { return susuGroupId; }
    public UUID    getMemberUserId()           { return memberUserId; }
    public long    getExpectedAmount()         { return expectedAmount; }
    public Long    getCollectedAmount()        { return collectedAmount; }
    public String  getStatus()                 { return status; }
    public int     getCollectionAttemptCount() { return collectionAttemptCount; }
    public long    getPenaltyAmount()          { return penaltyAmount; }
    public boolean isLate()                    { return isLate; }
    public UUID    getTransactionId()          { return transactionId; }
    public String  getTransactionReference()   { return transactionReference; }
    public Instant getPaidAt()                 { return paidAt; }
    public Instant getCreatedAt()              { return createdAt; }
}
