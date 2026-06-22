package com.stash.kyc.submission.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a submission transitions from PENDING_DOCUMENTS to
 * REVIEWING — i.e. all three required documents have been uploaded.
 *
 * <p>Consumed by the automated provider path (v0.2-023/024) and the
 * manual-review routing logic. Published to RabbitMQ via the KYC
 * Service's outbox pattern (per Folder Structure doc §4.2's outbox/ module).
 */
public record SubmissionReadyForReviewEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String sourceService,
        Instant occurredAt,
        String correlationId,
        Payload payload
) {
    public static final String EVENT_TYPE     = "SubmissionReadyForReview";
    public static final String SCHEMA_VERSION = "1.0";
    public static final String SOURCE_SERVICE = "kyc-service";

    public record Payload(UUID submissionId, UUID userId) {}
}
