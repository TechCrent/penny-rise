package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;
import java.time.Instant;
import java.util.UUID;

public class SusuRoundSkippedEvent extends ApplicationEvent {
    private final UUID    groupId;
    private final UUID    roundId;
    private final int     roundNumber;
    private final UUID    originalRecipientUserId;
    private final long    skippedPotAmount;
    private final String  correlationId;
    private final Instant occurredAt;

    public SusuRoundSkippedEvent(Object source, UUID groupId, UUID roundId,
                                  int roundNumber, UUID originalRecipientUserId,
                                  long skippedPotAmount,
                                  String correlationId, Instant occurredAt) {
        super(source);
        this.groupId                 = groupId;
        this.roundId                 = roundId;
        this.roundNumber             = roundNumber;
        this.originalRecipientUserId = originalRecipientUserId;
        this.skippedPotAmount        = skippedPotAmount;
        this.correlationId           = correlationId;
        this.occurredAt              = occurredAt;
    }

    public UUID    getGroupId()                 { return groupId; }
    public UUID    getRoundId()                 { return roundId; }
    public int     getRoundNumber()             { return roundNumber; }
    public UUID    getOriginalRecipientUserId() { return originalRecipientUserId; }
    public long    getSkippedPotAmount()        { return skippedPotAmount; }
    public String  getCorrelationId()           { return correlationId; }
    public Instant getOccurredAt()              { return occurredAt; }
}