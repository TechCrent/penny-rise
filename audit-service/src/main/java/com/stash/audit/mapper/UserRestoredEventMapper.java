package com.stash.audit.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Maps com.stash.admin.event.UserRestoredEvent (v0.5-006). */
@Component
public class UserRestoredEventMapper implements EventAuditMapper {

    @Override
    public String supportedEventType() { return "UserRestoredEvent"; }

    @Override
    public EventAuditMapping map(JsonNode payload) {
        return new EventAuditMapping(
                "ADMIN", UUID.fromString(payload.get("adminAccountId").asText()),
                "USER",  UUID.fromString(payload.get("userId").asText()),
                Instant.parse(payload.get("occurredAt").asText())
        );
    }
}
