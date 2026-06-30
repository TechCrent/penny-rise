package com.stash.admin.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disputes", schema = "admin")
public class DisputeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "raised_by_user_id",   nullable = false) private UUID    raisedByUserId;
    @Column(name = "dispute_type",         nullable = false) private String  disputeType;
    @Column(name = "related_entity_type",  nullable = false) private String  relatedEntityType;
    @Column(name = "related_entity_id",    nullable = false) private UUID    relatedEntityId;
    @Column(name = "subject",              nullable = false) private String  subject;
    @Column(name = "description",          nullable = false) private String  description;
    @Column(name = "status",               nullable = false) private String  status;
    @Column(name = "priority",             nullable = false) private String  priority;
    @Column(name = "assigned_to_admin_id")                   private UUID    assignedToAdminId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolution", columnDefinition = "jsonb")
    private String resolutionJson;

    @Column(name = "resolved_at")                            private Instant resolvedAt;
    @Column(name = "resolved_by_admin_id")                   private UUID    resolvedByAdminId;
    @Column(name = "created_at",           nullable = false) private Instant createdAt;
    @Column(name = "updated_at",           nullable = false) private Instant updatedAt;

    protected DisputeEntity() {}

    public static DisputeEntity create(UUID raisedByUserId, String disputeType,
                                       String relatedEntityType, UUID relatedEntityId,
                                       String subject, String description, Instant now) {
        DisputeEntity d = new DisputeEntity();
        d.raisedByUserId    = raisedByUserId;
        d.disputeType       = disputeType;
        d.relatedEntityType = relatedEntityType;
        d.relatedEntityId   = relatedEntityId;
        d.subject           = subject;
        d.description       = description;
        d.status            = "OPEN";
        d.priority          = "NORMAL";
        d.createdAt         = now;
        d.updatedAt         = now;
        return d;
    }

    public UUID    getId()                { return id; }
    public UUID    getRaisedByUserId()    { return raisedByUserId; }
    public String  getDisputeType()       { return disputeType; }
    public String  getRelatedEntityType() { return relatedEntityType; }
    public UUID    getRelatedEntityId()   { return relatedEntityId; }
    public String  getSubject()           { return subject; }
    public String  getDescription()       { return description; }
    public String  getStatus()            { return status; }
    public String  getPriority()          { return priority; }
    public UUID    getAssignedToAdminId() { return assignedToAdminId; }
    public String  getResolutionJson()    { return resolutionJson; }
    public Instant getResolvedAt()        { return resolvedAt; }
    public UUID    getResolvedByAdminId() { return resolvedByAdminId; }
    public Instant getCreatedAt()         { return createdAt; }
    public Instant getUpdatedAt()         { return updatedAt; }

    // State-transition methods used by v0.5-008 (dispute queue and resolution).

    public void assignTo(UUID adminId, Instant now) {
        this.assignedToAdminId = adminId;
        if ("OPEN".equals(this.status)) {
            this.status = "IN_REVIEW";
        }
        this.updatedAt = now;
    }

    public void resolve(String resolutionJson, UUID resolvedByAdminId, Instant now) {
        this.status            = "RESOLVED";
        this.resolutionJson    = resolutionJson;
        this.resolvedAt        = now;
        this.resolvedByAdminId = resolvedByAdminId;
        this.updatedAt         = now;
    }

    public void closeNoAction(String resolutionJson, UUID resolvedByAdminId, Instant now) {
        this.status            = "CLOSED_NO_ACTION";
        this.resolutionJson    = resolutionJson;
        this.resolvedAt        = now;
        this.resolvedByAdminId = resolvedByAdminId;
        this.updatedAt         = now;
    }
}
