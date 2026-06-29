package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SusuActivatedEvent extends ApplicationEvent {

    private final UUID         groupId;
    private final String       groupName;
    private final UUID         organiserUserId;
    private final List<UUID>   memberUserIds;
    private final UUID         ledgerAccountId;
    private final Instant      activatedAt;
    private final String       correlationId;

    public SusuActivatedEvent(Object source, UUID groupId, String groupName,
                               UUID organiserUserId, List<UUID> memberUserIds,
                               UUID ledgerAccountId, Instant activatedAt,
                               String correlationId) {
        super(source);
        this.groupId         = groupId;
        this.groupName       = groupName;
        this.organiserUserId = organiserUserId;
        this.memberUserIds   = memberUserIds;
        this.ledgerAccountId = ledgerAccountId;
        this.activatedAt     = activatedAt;
        this.correlationId   = correlationId;
    }

    public UUID         getGroupId()         { return groupId; }
    public String       getGroupName()       { return groupName; }
    public UUID         getOrganiserUserId() { return organiserUserId; }
    public List<UUID>   getMemberUserIds()   { return memberUserIds; }
    public UUID         getLedgerAccountId() { return ledgerAccountId; }
    public Instant      getActivatedAt()     { return activatedAt; }
    public String       getCorrelationId()   { return correlationId; }
}
