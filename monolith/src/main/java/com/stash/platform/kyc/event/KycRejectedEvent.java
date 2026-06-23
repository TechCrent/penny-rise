package com.stash.platform.kyc.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound mirror of kyc-service's {@code KycRejectedEvent} JSON payload.
 *
 * <p>Consumed by the monolith to set {@code users.kyc_status = REJECTED}.
 */
public record KycRejectedEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String sourceService,
        Instant occurredAt,
        String correlationId,
        Payload payload
) {
    public static final String EVENT_TYPE = "KycRejected";

    public record Payload(UUID submissionId, UUID userId, String reason) {}
}
