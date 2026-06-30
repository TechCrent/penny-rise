package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

public class SusuRoundCompletedEvent extends ApplicationEvent {

    private final UUID    groupId;
    private final UUID    roundId;
    private final int     roundNumber;
    private final int     totalRounds;
    private final UUID    recipientUserId;
    private final long    disbursedAmount;
    private final UUID    disbursementTransactionId;
    private final String  correlationId;
    private final Instant occurredAt;

    public SusuRoundCompletedEvent(Object source, UUID groupId, UUID roundId,
                                    int roundNumber, int totalRounds,
                                    UUID recipientUserId, long disbursedAmount,
                                    UUID disbursementTransactionId,
                                    String correlationId, Instant occurredAt) {
        super(source);
        this.groupId                   = groupId;
        this.roundId                   = roundId;
        this.roundNumber               = roundNumber;
        this.totalRounds               = totalRounds;
        this.recipientUserId           = recipientUserId;
        this.disbursedAmount           = disbursedAmount;
        this.disbursementTransactionId = disbursementTransactionId;
        this.correlationId             = correlationId;
        this.occurredAt                = occurredAt;
    }

    public UUID    getGroupId()                   { return groupId; }
    public UUID    getRoundId()                   { return roundId; }
    public int     getRoundNumber()               { return roundNumber; }
    public int     getTotalRounds()               { return totalRounds; }
    public UUID    getRecipientUserId()           { return recipientUserId; }
    public long    getDisbursedAmount()           { return disbursedAmount; }
    public UUID    getDisbursementTransactionId() { return disbursementTransactionId; }
    public String  getCorrelationId()             { return correlationId; }
    public Instant getOccurredAt()                { return occurredAt; }
}
