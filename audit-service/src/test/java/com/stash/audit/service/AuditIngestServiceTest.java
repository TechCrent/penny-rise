package com.stash.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.audit.consumer.IncomingAuditEvent;
import com.stash.audit.mapper.EventAuditMapper;
import com.stash.audit.mapper.EventAuditMapperRegistry;
import com.stash.audit.mapper.EventAuditMapping;
import com.stash.audit.repository.AuditLogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditIngestServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-01T09:00:00Z"), ZoneOffset.UTC);

    private final AuditLogEntryRepository repository = mock(AuditLogEntryRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        reset(repository);
    }

    @Test
    @DisplayName("a known event_type is mapped via its registered mapper and saved")
    void knownEventTypeMapsAndSaves() {
        EventAuditMapper mapper = mock(EventAuditMapper.class);
        when(mapper.supportedEventType()).thenReturn("UserSuspendedEvent");
        when(mapper.map(any())).thenReturn(new EventAuditMapping(
                "ADMIN", UUID.randomUUID(), "USER", UUID.randomUUID(), Instant.now(FIXED_CLOCK)));

        var registry = new EventAuditMapperRegistry(List.of(mapper));
        var service  = new AuditIngestService(repository, registry, objectMapper, FIXED_CLOCK);

        when(repository.existsByEventId("evt-1")).thenReturn(false);

        var event = new IncomingAuditEvent("evt-1", "UserSuspendedEvent", "monolith", "corr-1",
                objectMapper.createObjectNode());

        service.ingest(event);

        verify(repository).save(argThat(e ->
                "UserSuspendedEvent".equals(e.getEventType()) && "ADMIN".equals(e.getActorType())));
    }

    @Test
    @DisplayName("duplicate event_id (pre-check) is a no-op — no save attempted")
    void duplicateEventIdPreCheckIsNoOp() {
        var registry = new EventAuditMapperRegistry(List.of());
        var service  = new AuditIngestService(repository, registry, objectMapper, FIXED_CLOCK);

        when(repository.existsByEventId("evt-dup")).thenReturn(true);

        var event = new IncomingAuditEvent("evt-dup", "AnyEvent", "monolith", null,
                objectMapper.createObjectNode());

        assertThatCode(() -> service.ingest(event)).doesNotThrowAnyException();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("duplicate event_id (race, caught at insert) is swallowed, not propagated")
    void duplicateEventIdRaceIsSwallowed() {
        var registry = new EventAuditMapperRegistry(List.of());
        var service  = new AuditIngestService(repository, registry, objectMapper, FIXED_CLOCK);

        when(repository.existsByEventId("evt-race")).thenReturn(false);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("unique violation"));

        var event = new IncomingAuditEvent("evt-race", "AnyEvent", "monolith", null,
                objectMapper.createObjectNode());

        assertThatCode(() -> service.ingest(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unrecognised event_type is stored with SYSTEM actor, UNKNOWN target — never dropped")
    void unrecognisedEventTypeStoredWithFallback() {
        var registry = new EventAuditMapperRegistry(List.of());
        var service  = new AuditIngestService(repository, registry, objectMapper, FIXED_CLOCK);

        when(repository.existsByEventId("evt-2")).thenReturn(false);

        var payload = objectMapper.createObjectNode().put("something", "specific to a future event type");
        var event   = new IncomingAuditEvent("evt-2", "SomeBrandNewEventType", "susu", "corr-2", payload);

        service.ingest(event);

        verify(repository).save(argThat(e ->
                "SomeBrandNewEventType".equals(e.getEventType())
                && "SYSTEM".equals(e.getActorType())
                && "UNKNOWN".equals(e.getTargetType())));
    }

    @Test
    @DisplayName("unrecognised event with no parseable timestamp falls back to received time, not an exception")
    void unrecognisedEventWithNoTimestampDoesNotThrow() {
        var registry = new EventAuditMapperRegistry(List.of());
        var service  = new AuditIngestService(repository, registry, objectMapper, FIXED_CLOCK);

        when(repository.existsByEventId("evt-3")).thenReturn(false);

        var payload = objectMapper.createObjectNode().put("no_timestamp_field", true);
        var event   = new IncomingAuditEvent("evt-3", "AnotherUnknownType", "vault", null, payload);

        assertThatCode(() -> service.ingest(event)).doesNotThrowAnyException();
        verify(repository).save(any());
    }
}
