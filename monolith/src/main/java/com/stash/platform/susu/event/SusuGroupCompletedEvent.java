package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

public class SusuGroupCompletedEvent extends ApplicationEvent {

    private final UUID    groupId;
    private final String  groupName;
    private final UUID    organiserUserId;
    private final int     totalRounds;
    private final String  correlationId;
    private final Instant occurredAt;

    public SusuGroupCompletedEvent(Object source, UUID groupId, String groupName,
                                    UUID organiserUserId, int totalRounds,
                                    String correlationId, Instant occurredAt) {
        super(source);
        this.groupId         = groupId;
        this.groupName       = groupName;
        this.organiserUserId = organiserUserId;
        this.totalRounds     = totalRounds;
        this.correlationId   = correlationId;
        this.occurredAt      = occurredAt;
    }

    public UUID    getGroupId()         { return groupId; }
    public String  getGroupName()       { return groupName; }
    public UUID    getOrganiserUserId() { return organiserUserId; }
    public int     getTotalRounds()     { return totalRounds; }
    public String  getCorrelationId()   { return correlationId; }
    public Instant getOccurredAt()      { return occurredAt; }
}
