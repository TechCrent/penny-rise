package com.stash.audit.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps DepositCompleted per System Design §10 worked example.
 * Field names (user_id, vault_id, occurred_at) are assumed from the example
 * description — confirm against payments-service's actual event class once visible.
 */
@Component
public class DepositCompletedEventMapper implements EventAuditMapper {

    @Override
    public String supportedEventType() { return "DepositCompleted"; }

    @Override
    public EventAuditMapping map(JsonNode payload) {
        return new EventAuditMapping(
                "USER",  UUID.fromString(payload.get("user_id").asText()),
                "VAULT", UUID.fromString(payload.get("vault_id").asText()),
                Instant.parse(payload.get("occurred_at").asText())
        );
    }
}
