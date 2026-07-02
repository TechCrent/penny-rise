package com.stash.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogQueryFilter(
        UUID actorId, String actorType,
        UUID targetEntityId, String targetEntityType,
        String eventType, Instant fromDate, Instant toDate
) {}
