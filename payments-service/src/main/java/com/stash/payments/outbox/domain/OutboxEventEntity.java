package com.stash.payments.outbox.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for {@code outbox.outbox_events}.
 *
 * <p>Append-only at the application layer: after construction and INSERT,
 * only {@code status}, {@code attempts}, {@code sent_at}, and
 * {@code last_attempted_at} are ever written (by the relay worker, not
 * by this class). The Postgres role-level grant enforces the same constraint
 * at the DB layer (v0.3-006).
 *
 * <p>The four relay-tracking fields are deliberately NOT final so the
 * relay worker ({@code OutboxRelay}, v0.3-011) can update them via a
 * separate JPA save call. Everything else — payload, event_type, routing_key,
 * aggregate context, schema_version, created_at — must never change after
 * INSERT.
 */
@Entity
@Table(name = "outbox_events", schema = "outbox")
public class OutboxEventEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    // ── Immutable after INSERT ─────────────────────────────────────────────

    @Column(name = "event_type",     nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "schema_version", nullable = false, updatable = false, length = 20)
    private String schemaVersion;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id",   nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "payload",        nullable = false, updatable = false,
            columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "routing_key",    nullable = false, updatable = false, length = 255)
    private String routingKey;

    @Column(name = "correlation_id", updatable = false, length = 255)
    private String correlationId;

    @Column(name = "created_at",     nullable = false, updatable = false)
    private Instant createdAt;

    // ── Relay-tracking fields (updatable by OutboxRelay only) ─────────────

    @Column(name = "status",             nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private OutboxEventStatus status;

    @Column(name = "attempts",           nullable = false)
    private int attempts;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    protected OutboxEventEntity() {}

    /**
     * Callers go through {@link com.stash.payments.outbox.service.OutboxPublisher}.
     */
    public OutboxEventEntity(String eventType, String schemaVersion,
                      String aggregateType, UUID aggregateId,
                      String payload, String routingKey,
                      String correlationId, Instant createdAt) {
        this.eventType     = eventType;
        this.schemaVersion = schemaVersion;
        this.aggregateType = aggregateType;
        this.aggregateId   = aggregateId;
        this.payload       = payload;
        this.routingKey    = routingKey;
        this.correlationId = correlationId;
        this.createdAt     = createdAt;
        this.status        = OutboxEventStatus.PENDING;
        this.attempts      = 0;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public UUID   getId()              { return id; }
    public String getEventType()       { return eventType; }
    public String getSchemaVersion()   { return schemaVersion; }
    public String getAggregateType()   { return aggregateType; }
    public UUID   getAggregateId()     { return aggregateId; }
    public String getPayload()         { return payload; }
    public String getRoutingKey()      { return routingKey; }
    public String getCorrelationId()   { return correlationId; }
    public Instant getCreatedAt()      { return createdAt; }
    public OutboxEventStatus getStatus()     { return status; }
    public int    getAttempts()              { return attempts; }
    public Instant getSentAt()               { return sentAt; }
    public Instant getLastAttemptedAt()      { return lastAttemptedAt; }

    // ── Relay mutations — called ONLY by OutboxRelay ───────────────────────

    public void markSent(Instant now) {
        this.status           = OutboxEventStatus.SENT;
        this.sentAt           = now;
        this.lastAttemptedAt  = now;
        this.attempts        += 1;
    }

    public void recordFailedAttempt(Instant now) {
        this.status           = OutboxEventStatus.FAILED;
        this.lastAttemptedAt  = now;
        this.attempts        += 1;
    }

    public void incrementAttempt(Instant now) {
        this.lastAttemptedAt = now;
        this.attempts       += 1;
    }
}
