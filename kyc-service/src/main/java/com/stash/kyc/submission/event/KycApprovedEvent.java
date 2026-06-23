package com.stash.kyc.submission.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a KYC submission is approved (AUTO or MANUAL path).
 *
 * <p>Consumed by the monolith (v0.2-026) to set users.kyc_status = VERIFIED
 * and users.ghana_card_number — note the terminal status NAME differs
 * between databases: this service's kyc.submissions.status = APPROVED,
 * while the monolith's users.kyc_status = VERIFIED (System Design Decision 6).
 * The event payload carries the raw ghanaCardNumber so the monolith can
 * populate its own copy of the field — KYC and monolith are separate
 * databases with no shared storage.
 *
 * <p><strong>PII note:</strong> this event payload contains ghanaCardNumber
 * in plaintext (decrypted) because it crosses a trusted internal service
 * boundary over AMQPS (TLS-wrapped, per System Design §10.3) to populate
 * the monolith's own encrypted-at-rest-by-provider users table. It is
 * never logged in transit — RabbitMQ message bodies are not logged by
 * the correlation/logging infrastructure built in v0.1-013/014.
 */

public record KycApprovedEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String sourceService,
        Instant occurredAt,
        String correlationId,
        Payload payload
) {
    public static final String EVENT_TYPE     = "KycApproved";
    public static final String SCHEMA_VERSION = "1.0";
    public static final String SOURCE_SERVICE = "kyc-service";
    public record Payload(UUID submissionId, UUID userId, String ghanaCardNumber) {}
}