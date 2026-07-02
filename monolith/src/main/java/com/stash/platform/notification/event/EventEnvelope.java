package com.stash.platform.notification.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Real event envelope shape per System Design §4.2.
 * Corrects v0.5-009's assumption that event_id/event_type/correlation_id
 * were AMQP message properties — they are in the JSON body itself.
 */
public record EventEnvelope(
        String eventId, String eventType, String schemaVersion, String sourceService,
        Instant occurredAt, String correlationId, JsonNode payload
) {}
