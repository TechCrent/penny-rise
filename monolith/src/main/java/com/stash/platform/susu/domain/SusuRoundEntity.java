package com.stash.platform.susu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "susu_rounds", schema = "susu")
public class SusuRoundEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "susu_group_id", nullable = false)
    private UUID susuGroupId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "scheduled_collection_at")
    private Instant scheduledCollectionAt;

    @Column(name = "expected_pot_amount")
    private Long expectedPotAmount;

    @Column(name = "actual_pot_amount")
    private Long actualPotAmount;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "disbursement_transaction_id")
    private UUID disbursementTransactionId;

    protected SusuRoundEntity() {}

    public UUID    getId()                        { return id; }
    public UUID    getSusuGroupId()               { return susuGroupId; }
    public int     getRoundNumber()               { return roundNumber; }
    public UUID    getRecipientUserId()           { return recipientUserId; }
    public String  getStatus()                    { return status; }
    public Instant getScheduledCollectionAt()     { return scheduledCollectionAt; }
    public Long    getExpectedPotAmount()         { return expectedPotAmount; }
    public Long    getActualPotAmount()           { return actualPotAmount; }
    public Instant getDisbursedAt()               { return disbursedAt; }
    public UUID    getDisbursementTransactionId() { return disbursementTransactionId; }
}
