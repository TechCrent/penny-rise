package com.stash.audit.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps com.stash.admin.event.DisputeRaisedEvent (v0.5-007).
 * Actor is the raising USER, target is the DISPUTE itself — the polymorphic
 * related_entity stays inside the raw payload JSON.
 */
@Component
public class DisputeRaisedEventMapper implements EventAuditMapper {

    @Override
    public String supportedEventType() { return "DisputeRaisedEvent"; }

    @Override
    public EventAuditMapping map(JsonNode payload) {
        return new EventAuditMapping(
                "USER",    UUID.fromString(payload.get("raisedByUserId").asText()),
                "DISPUTE", UUID.fromString(payload.get("disputeId").asText()),
                Instant.parse(payload.get("occurredAt").asText())
        );
    }
}
