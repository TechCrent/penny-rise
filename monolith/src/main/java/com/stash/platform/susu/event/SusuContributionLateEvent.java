package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

public class SusuContributionLateEvent extends ApplicationEvent {

    private final UUID    groupId;
    private final UUID    roundId;
    private final UUID    contributionId;
    private final UUID    memberUserId;
    private final long    penaltyAmount;
    private final boolean penaltyWaived;
    private final String  correlationId;
    private final Instant occurredAt;

    public SusuContributionLateEvent(Object source, UUID groupId, UUID roundId,
                                      UUID contributionId, UUID memberUserId,
                                      long penaltyAmount, boolean penaltyWaived,
                                      String correlationId, Instant occurredAt) {
        super(source);
        this.groupId        = groupId;
        this.roundId        = roundId;
        this.contributionId = contributionId;
        this.memberUserId   = memberUserId;
        this.penaltyAmount  = penaltyAmount;
        this.penaltyWaived  = penaltyWaived;
        this.correlationId  = correlationId;
        this.occurredAt     = occurredAt;
    }

    public UUID    getGroupId()        { return groupId; }
    public UUID    getRoundId()        { return roundId; }
    public UUID    getContributionId() { return contributionId; }
    public UUID    getMemberUserId()   { return memberUserId; }
    public long    getPenaltyAmount()  { return penaltyAmount; }
    public boolean isPenaltyWaived()   { return penaltyWaived; }
    public String  getCorrelationId()  { return correlationId; }
    public Instant getOccurredAt()     { return occurredAt; }
}
