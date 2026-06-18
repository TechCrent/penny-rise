package com.stash.platform.user.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted when a refresh token replay attack is detected.
 *
 * <p>The notification worker (v0.5) consumes this event to alert
 * the user via push and email. It is published to RabbitMQ via
 * the monolith.events exchange.
 */
public record RefreshTokenReplayDetectedEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String sourceService,
        Instant occurredAt,
        String correlationId,
        Payload payload
) {
    public static final String EVENT_TYPE     = "RefreshTokenReplayDetected";
    public static final String SCHEMA_VERSION = "1.0";
    public static final String SOURCE_SERVICE = "monolith";

    public record Payload(
            UUID userId,
            UUID replayedTokenId,
            int descendantsRevoked
    ) {}
}