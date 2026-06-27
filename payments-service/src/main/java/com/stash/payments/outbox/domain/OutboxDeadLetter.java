package com.stash.payments.outbox.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_dead_letters", schema = "outbox")
public class OutboxDeadLetter {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "original_event_id", nullable = false, unique = true)
    private UUID originalEventId;

    @Column(name = "event_type",         nullable = false, length = 100)
    private String eventType;

    @Column(name = "schema_version",     nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "aggregate_type",     nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id",       nullable = false)
    private UUID aggregateId;

    @Column(name = "payload",            nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "routing_key",        nullable = false, length = 255)
    private String routingKey;

    @Column(name = "correlation_id",     length = 255)
    private String correlationId;

    @Column(name = "original_created_at", nullable = false)
    private Instant originalCreatedAt;

    @Column(name = "failed_attempts",    nullable = false)
    private int failedAttempts;

    @Column(name = "failed_reason")
    private String failedReason;

    @Column(name = "dead_lettered_at",   nullable = false)
    private Instant deadLetteredAt;

    @Column(name = "requeue_requested",  nullable = false)
    private boolean requeueRequested;

    protected OutboxDeadLetter() {}

    public OutboxDeadLetter(OutboxEventEntity source, String failedReason, Instant now) {
        this.originalEventId   = source.getId();
        this.eventType         = source.getEventType();
        this.schemaVersion     = source.getSchemaVersion();
        this.aggregateType     = source.getAggregateType();
        this.aggregateId       = source.getAggregateId();
        this.payload           = source.getPayload();
        this.routingKey        = source.getRoutingKey();
        this.correlationId     = source.getCorrelationId();
        this.originalCreatedAt = source.getCreatedAt();
        this.failedAttempts    = source.getAttempts();
        this.failedReason      = failedReason;
        this.deadLetteredAt    = now;
        this.requeueRequested  = false;
    }

    // Getters
    public UUID getId()                  { return id; }
    public UUID getOriginalEventId()     { return originalEventId; }
    public String getEventType()         { return eventType; }
    public int getFailedAttempts()       { return failedAttempts; }
    public String getFailedReason()      { return failedReason; }
    public Instant getDeadLetteredAt()   { return deadLetteredAt; }
    public boolean isRequeueRequested()  { return requeueRequested; }
}
