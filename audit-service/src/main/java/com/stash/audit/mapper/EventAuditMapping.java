package com.stash.audit.mapper;

import java.time.Instant;
import java.util.UUID;

/** The structured columns a mapper produces from an event's payload. */
public record EventAuditMapping(
        String actorType,
        UUID actorId,
        String targetType,
        UUID targetId,
        Instant occurredAt
) {}
