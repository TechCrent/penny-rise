package com.stash.challenge.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_deposit_events", schema = "challenge")
public class ProcessedDepositEventEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "event_id",     nullable = false, length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedDepositEventEntity() {}

    public static ProcessedDepositEventEntity create(String eventId, Instant processedAt) {
        ProcessedDepositEventEntity e = new ProcessedDepositEventEntity();
        e.id          = UUID.randomUUID();
        e.eventId     = eventId;
        e.processedAt = processedAt;
        return e;
    }

    public UUID   getId()          { return id; }
    public String getEventId()     { return eventId; }
}
