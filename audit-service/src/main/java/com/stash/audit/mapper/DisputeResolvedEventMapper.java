package com.stash.audit.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps com.stash.admin.event.DisputeResolvedEvent (v0.5-008).
 * Actor is the resolving ADMIN, not the original raising user.
 */
@Component
public class DisputeResolvedEventMapper implements EventAuditMapper {

    @Override
    public String supportedEventType() { return "DisputeResolvedEvent"; }

    @Override
    public EventAuditMapping map(JsonNode payload) {
        return new EventAuditMapping(
                "ADMIN",   UUID.fromString(payload.get("resolvedByAdminId").asText()),
                "DISPUTE", UUID.fromString(payload.get("disputeId").asText()),
                Instant.parse(payload.get("occurredAt").asText())
        );
    }
}
