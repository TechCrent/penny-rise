package com.stash.audit.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Raw row shape as it comes off the DB — DB column names preserved
 * (target_type/id), not yet remapped to the API's target_entity_type/id naming.
 */
public record AuditLogEntryRow(
        String eventId, String eventType, String actorType, UUID actorId,
        String targetType, UUID targetId,
        String payload, Instant occurredAt, UUID id
) {}
