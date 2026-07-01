package com.stash.audit.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log_entries", schema = "audit")
public class AuditLogEntryEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true) private String  eventId;
    @Column(name = "event_type", nullable = false)              private String  eventType;
    @Column(name = "schema_version")                            private String  schemaVersion;
    @Column(name = "source_service", nullable = false)          private String  sourceService;
    @Column(name = "actor_type", nullable = false)              private String  actorType;
    @Column(name = "actor_id")                                  private UUID    actorId;
    @Column(name = "target_type", nullable = false)             private String  targetType;
    @Column(name = "target_id")                                 private UUID    targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "correlation_id")                            private String  correlationId;
    @Column(name = "occurred_at", nullable = false)             private Instant occurredAt;
    @Column(name = "received_at", nullable = false)             private Instant receivedAt;

    protected AuditLogEntryEntity() {}

    public static AuditLogEntryEntity create(String eventId, String eventType, String schemaVersion,
                                              String sourceService, String actorType, UUID actorId,
                                              String targetType, UUID targetId, String payload,
                                              String correlationId, Instant occurredAt, Instant receivedAt) {
        AuditLogEntryEntity e = new AuditLogEntryEntity();
        e.eventId        = eventId;
        e.eventType      = eventType;
        e.schemaVersion  = schemaVersion;
        e.sourceService  = sourceService;
        e.actorType      = actorType;
        e.actorId        = actorId;
        e.targetType     = targetType;
        e.targetId       = targetId;
        e.payload        = payload;
        e.correlationId  = correlationId;
        e.occurredAt     = occurredAt;
        e.receivedAt     = receivedAt;
        return e;
    }

    public UUID   getId()         { return id; }
    public String getEventId()    { return eventId; }
    public String getEventType()  { return eventType; }
    public String getActorType()  { return actorType; }
    public String getTargetType() { return targetType; }
}
