package com.stash.platform.kyc.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound mirror of kyc-service's {@code KycApprovedEvent} JSON payload.
 *
 * <p>Consumed by the monolith to set {@code users.kyc_status = APPROVED} and
 * populate {@code users.ghana_card_number} after a terminal KYC approval.
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
    public static final String EVENT_TYPE = "KycApproved";

    public record Payload(UUID submissionId, UUID userId, String ghanaCardNumber) {}
}
