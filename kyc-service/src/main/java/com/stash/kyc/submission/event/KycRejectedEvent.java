package com.stash.kyc.submission.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a KYC submission is rejected. Consumed by the monolith to
 * set users.kyc_status = REJECTED, or RESUBMISSION_REQUIRED if
 * rejectionCount indicates this is the user's second (or later)
 * rejection (v0.5-035) — see KycSubmissionRepository.countByUserIdAndStatus.
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
    public static final String EVENT_TYPE     = "KycRejected";
    public static final String SCHEMA_VERSION = "1.0";
    public static final String SOURCE_SERVICE = "kyc-service";
    public record Payload(UUID submissionId, UUID userId, String reason, int rejectionCount) {}
}