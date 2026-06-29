package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

public class SusuRoundStartedEvent extends ApplicationEvent {

    private final UUID    groupId;
    private final UUID    roundId;
    private final int     roundNumber;
    private final int     totalRounds;
    private final UUID    recipientUserId;
    private final Instant scheduledCollectionAt;
    private final String  correlationId;

    public SusuRoundStartedEvent(Object source, UUID groupId, UUID roundId,
                                  int roundNumber, int totalRounds,
                                  UUID recipientUserId, Instant scheduledCollectionAt,
                                  String correlationId) {
        super(source);
        this.groupId               = groupId;
        this.roundId               = roundId;
        this.roundNumber           = roundNumber;
        this.totalRounds           = totalRounds;
        this.recipientUserId       = recipientUserId;
        this.scheduledCollectionAt = scheduledCollectionAt;
        this.correlationId         = correlationId;
    }

    public UUID    getGroupId()               { return groupId; }
    public UUID    getRoundId()               { return roundId; }
    public int     getRoundNumber()           { return roundNumber; }
    public int     getTotalRounds()           { return totalRounds; }
    public UUID    getRecipientUserId()       { return recipientUserId; }
    public Instant getScheduledCollectionAt() { return scheduledCollectionAt; }
    public String  getCorrelationId()         { return correlationId; }
}