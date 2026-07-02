package com.stash.audit.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Field names follow the AC's contract (target_entity_type/id), which differ
 * from the underlying DB columns (target_type/target_id per Schema doc §9.1).
 * The mapping happens once in AuditLogQueryService.
 */
public record AuditLogEntryResponse(
        String eventId, String eventType, String actorType, UUID actorId,
        String targetEntityType, UUID targetEntityId,
        JsonNode payload, Instant occurredAt
) {}
