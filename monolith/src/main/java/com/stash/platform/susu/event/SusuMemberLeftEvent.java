package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;
import java.time.Instant;
import java.util.UUID;

public class SusuMemberLeftEvent extends ApplicationEvent {
    private final UUID    groupId;
    private final UUID    userId;
    private final String  reason;
    private final String  groupStatus;
    private final boolean groupCancelled;
    private final String  correlationId;
    private final Instant occurredAt;

    public SusuMemberLeftEvent(Object source, UUID groupId, UUID userId,
                                String reason, String groupStatus,
                                boolean groupCancelled,
                                String correlationId, Instant occurredAt) {
        super(source);
        this.groupId        = groupId;
        this.userId         = userId;
        this.reason         = reason;
        this.groupStatus    = groupStatus;
        this.groupCancelled = groupCancelled;
        this.correlationId  = correlationId;
        this.occurredAt     = occurredAt;
    }

    public UUID    getGroupId()       { return groupId; }
    public UUID    getUserId()        { return userId; }
    public String  getReason()        { return reason; }
    public String  getGroupStatus()   { return groupStatus; }
    public boolean isGroupCancelled() { return groupCancelled; }
    public String  getCorrelationId() { return correlationId; }
    public Instant getOccurredAt()    { return occurredAt; }
}