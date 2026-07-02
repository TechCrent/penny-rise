package com.stash.platform.notification.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_worker_events", schema = "notification")
public class ProcessedWorkerEventEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "event_id",     nullable = false, length = 255)
    private String eventId;

    @Column(name = "event_type",   nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedWorkerEventEntity() {}

    public static ProcessedWorkerEventEntity create(String eventId, String eventType, Instant processedAt) {
        ProcessedWorkerEventEntity e = new ProcessedWorkerEventEntity();
        e.id          = UUID.randomUUID();
        e.eventId     = eventId;
        e.eventType   = eventType;
        e.processedAt = processedAt;
        return e;
    }

    public UUID   getId()          { return id; }
    public String getEventId()     { return eventId; }
    public String getEventType()   { return eventType; }
}
