package com.stash.platform.kyc.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound mirror of kyc-service's {@code KycRejectedEvent} JSON payload.
 *
 * <p>Consumed by the monolith to set {@code users.kyc_status = REJECTED},
 * or {@code RESUBMISSION_REQUIRED} if {@code rejectionCount >= 2}
 * (v0.5-035) — see {@link com.stash.platform.kyc.service.KycUserSyncService}.
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

    public record Payload(UUID submissionId, UUID userId, String reason, int rejectionCount) {}
}
