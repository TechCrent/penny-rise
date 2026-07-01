package com.stash.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.audit.consumer.IncomingAuditEvent;
import com.stash.audit.domain.AuditLogEntryEntity;
import com.stash.audit.mapper.EventAuditMapping;
import com.stash.audit.mapper.EventAuditMapperRegistry;
import com.stash.audit.repository.AuditLogEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class AuditIngestService {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestService.class);

    private final AuditLogEntryRepository repository;
    private final EventAuditMapperRegistry mapperRegistry;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditIngestService(AuditLogEntryRepository repository,
                               EventAuditMapperRegistry mapperRegistry,
                               ObjectMapper objectMapper,
                               Clock clock) {
        this.repository    = repository;
        this.mapperRegistry = mapperRegistry;
        this.objectMapper  = objectMapper;
        this.clock         = clock;
    }

    /**
     * Idempotent by event_id. A duplicate delivery hits the DB's UNIQUE
     * constraint and is swallowed — the duplicate is acknowledged without
     * error (no exception propagates to the @RabbitListener) so no retry
     * is triggered.
     */
    @Transactional
    public void ingest(IncomingAuditEvent event) {
        if (repository.existsByEventId(event.eventId())) {
            log.debug("Duplicate event_id={} — already recorded, no-op ack", event.eventId());
            return;
        }

        EventAuditMapping mapping = mapperRegistry.find(event.eventType())
                .map(mapper -> mapper.map(event.payload()))
                .orElseGet(() -> fallbackMapping(event));

        AuditLogEntryEntity entity = AuditLogEntryEntity.create(
                event.eventId(), event.eventType(), schemaVersionOf(event.payload()),
                event.sourceService(), mapping.actorType(), mapping.actorId(),
                mapping.targetType(), mapping.targetId(),
                event.payload().toString(), event.correlationId(),
                mapping.occurredAt(), Instant.now(clock));

        try {
            repository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // Race: two deliveries of the same event_id passed the existsByEventId
            // check concurrently. Same outcome as the pre-check — no-op, no error.
            log.debug("Duplicate event_id={} caught at insert (concurrent delivery race)", event.eventId());
        }
    }

    /**
     * Unrecognised event types are stored — never dropped. SYSTEM/UNKNOWN with
     * NULL ids is the least-wrong default when the payload can't be classified.
     * occurred_at falls back to ingestion time when neither common timestamp
     * field name (occurredAt, occurred_at) is present or parseable.
     */
    private EventAuditMapping fallbackMapping(IncomingAuditEvent event) {
        Instant occurredAt = extractTimestampLeniently(event.payload())
                .orElseGet(() -> Instant.now(clock));
        return new EventAuditMapping("SYSTEM", null, "UNKNOWN", null, occurredAt);
    }

    private Optional<Instant> extractTimestampLeniently(JsonNode payload) {
        for (String field : new String[]{"occurredAt", "occurred_at"}) {
            JsonNode node = payload.get(field);
            if (node != null && node.isTextual()) {
                try {
                    return Optional.of(Instant.parse(node.asText()));
                } catch (Exception ignored) { /* fall through */ }
            }
        }
        return Optional.empty();
    }

    private String schemaVersionOf(JsonNode payload) {
        JsonNode v = payload.get("schema_version");
        return (v != null && v.isTextual()) ? v.asText() : "1.0";
    }
}
