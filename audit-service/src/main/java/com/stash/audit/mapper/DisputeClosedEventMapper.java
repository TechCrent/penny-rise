package com.stash.audit.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Maps com.stash.admin.event.DisputeClosedEvent (v0.5-008). */
@Component
public class DisputeClosedEventMapper implements EventAuditMapper {

    @Override
    public String supportedEventType() { return "DisputeClosedEvent"; }

    @Override
    public EventAuditMapping map(JsonNode payload) {
        return new EventAuditMapping(
                "ADMIN",   UUID.fromString(payload.get("closedByAdminId").asText()),
                "DISPUTE", UUID.fromString(payload.get("disputeId").asText()),
                Instant.parse(payload.get("occurredAt").asText())
        );
    }
}
