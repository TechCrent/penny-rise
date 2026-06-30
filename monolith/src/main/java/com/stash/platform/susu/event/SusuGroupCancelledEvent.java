package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SusuGroupCancelledEvent extends ApplicationEvent {

    private final UUID       groupId;
    private final String     groupName;
    private final UUID       organiserUserId;
    private final List<UUID> affectedMemberIds;
    private final String     correlationId;
    private final Instant    occurredAt;

    public SusuGroupCancelledEvent(Object source, UUID groupId, String groupName,
                                    UUID organiserUserId, List<UUID> affectedMemberIds,
                                    String correlationId, Instant occurredAt) {
        super(source);
        this.groupId           = groupId;
        this.groupName         = groupName;
        this.organiserUserId   = organiserUserId;
        this.affectedMemberIds = List.copyOf(affectedMemberIds);
        this.correlationId     = correlationId;
        this.occurredAt        = occurredAt;
    }

    public UUID       getGroupId()           { return groupId; }
    public String     getGroupName()         { return groupName; }
    public UUID       getOrganiserUserId()   { return organiserUserId; }
    public List<UUID> getAffectedMemberIds() { return affectedMemberIds; }
    public String     getCorrelationId()     { return correlationId; }
    public Instant    getOccurredAt()        { return occurredAt; }
}
