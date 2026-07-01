package com.stash.audit.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class EventAuditMapperRegistry {

    private static final Logger log = LoggerFactory.getLogger(EventAuditMapperRegistry.class);

    private final Map<String, EventAuditMapper> mappersByEventType;

    public EventAuditMapperRegistry(List<EventAuditMapper> mappers) {
        this.mappersByEventType = mappers.stream()
                .collect(Collectors.toMap(EventAuditMapper::supportedEventType, Function.identity()));
    }

    /**
     * Returns empty if no mapper is registered — the caller (AuditIngestService)
     * handles the "unrecognised event type" fallback per Issue v0.5-009.
     * Logs a WARN here, at the point of the miss, not in the caller.
     */
    public Optional<EventAuditMapper> find(String eventType) {
        EventAuditMapper mapper = mappersByEventType.get(eventType);
        if (mapper == null) {
            log.warn("No EventAuditMapper registered for event_type={} — storing with fallback SYSTEM/UNKNOWN actor/target",
                    eventType);
        }
        return Optional.ofNullable(mapper);
    }
}
