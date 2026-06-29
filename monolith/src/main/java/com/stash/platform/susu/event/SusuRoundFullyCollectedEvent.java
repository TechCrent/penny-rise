package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published AFTER_COMMIT when all contributions for a round are PAID.
 * The disbursement worker (v0.4-010) listens for this event to trigger pot payout.
 * This endpoint never triggers disbursement directly.
 */
public class SusuRoundFullyCollectedEvent extends ApplicationEvent {

    private final UUID    groupId;
    private final UUID    roundId;
    private final int     roundNumber;
    private final long    actualPotAmount;
    private final UUID    recipientUserId;
    private final String  correlationId;
    private final Instant occurredAt;

    public SusuRoundFullyCollectedEvent(Object source, UUID groupId, UUID roundId,
                                         int roundNumber, long actualPotAmount,
                                         UUID recipientUserId, String correlationId,
                                         Instant occurredAt) {
        super(source);
        this.groupId         = groupId;
        this.roundId         = roundId;
        this.roundNumber     = roundNumber;
        this.actualPotAmount = actualPotAmount;
        this.recipientUserId = recipientUserId;
        this.correlationId   = correlationId;
        this.occurredAt      = occurredAt;
    }

    public UUID    getGroupId()         { return groupId; }
    public UUID    getRoundId()         { return roundId; }
    public int     getRoundNumber()     { return roundNumber; }
    public long    getActualPotAmount() { return actualPotAmount; }
    public UUID    getRecipientUserId() { return recipientUserId; }
    public String  getCorrelationId()   { return correlationId; }
    public Instant getOccurredAt()      { return occurredAt; }
}
