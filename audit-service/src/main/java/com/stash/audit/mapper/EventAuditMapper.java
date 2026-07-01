package com.stash.audit.mapper;

import com.fasterxml.jackson.databind.JsonNode;

public interface EventAuditMapper {
    /** The exact event_type string this mapper handles, e.g. "UserSuspendedEvent". */
    String supportedEventType();

    EventAuditMapping map(JsonNode payload);
}
