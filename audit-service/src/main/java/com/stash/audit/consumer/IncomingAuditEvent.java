package com.stash.audit.consumer;

import com.fasterxml.jackson.databind.JsonNode;

/** The parsed shape of an inbound AMQP message, before mapping to audit columns. */
public record IncomingAuditEvent(
        String eventId,
        String eventType,
        String sourceService,
        String correlationId,
        JsonNode payload
) {}
