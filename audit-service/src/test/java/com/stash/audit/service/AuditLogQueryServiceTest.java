package com.stash.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.audit.api.dto.AuditLogQueryFilter;
import com.stash.audit.repository.AuditLogEntryRow;
import com.stash.audit.repository.AuditLogQueryRepository;
import com.stash.audit.repository.CursorPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {

    private static final AuditLogQueryFilter EMPTY_FILTER =
            new AuditLogQueryFilter(null, null, null, null, null, null, null);

    @Mock
    AuditLogQueryRepository repository;

    AuditLogQueryService service;

    @BeforeEach
    void setUp() {
        reset(repository);
        service = new AuditLogQueryService(repository, new ObjectMapper());
    }

    private AuditLogEntryRow stubRow(String eventId) {
        return new AuditLogEntryRow(eventId, "PAYMENT_INITIATED", "USER", UUID.randomUUID(),
                "WALLET", UUID.randomUUID(), "{}", Instant.now(), UUID.randomUUID());
    }

    @Test
    void unfilteredList() {
        when(repository.query(eq(EMPTY_FILTER), isNull(), eq(26))).thenReturn(List.of(stubRow("evt-1")));

        var result = service.list(EMPTY_FILTER, null, 25);

        assertThat(result.entries()).hasSize(1);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void hasMoreWhenExtraRowReturned() {
        var rows = List.of(stubRow("evt-1"), stubRow("evt-2"), stubRow("evt-3"));
        when(repository.query(eq(EMPTY_FILTER), isNull(), eq(3))).thenReturn(rows);

        var result = service.list(EMPTY_FILTER, null, 2);

        assertThat(result.entries()).hasSize(2);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("evt-2");
    }

    @Test
    void cursorResolved() {
        var cursorPos = new CursorPosition(UUID.randomUUID(), Instant.now());
        when(repository.resolveCursor("evt-cursor")).thenReturn(Optional.of(cursorPos));
        when(repository.query(eq(EMPTY_FILTER), eq(cursorPos), eq(26))).thenReturn(List.of());

        var result = service.list(EMPTY_FILTER, "evt-cursor", 25);

        assertThat(result.entries()).isEmpty();
        verify(repository).resolveCursor("evt-cursor");
        verify(repository).query(eq(EMPTY_FILTER), eq(cursorPos), eq(26));
    }

    @Test
    void invalidCursorThrows400() {
        when(repository.resolveCursor("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(EMPTY_FILTER, "bad-id", 25))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INVALID_CURSOR");
    }

    @Test
    void filterByActor() {
        var actorId = UUID.randomUUID();
        var filter  = new AuditLogQueryFilter(actorId, "USER", null, null, null, null, null);
        when(repository.query(eq(filter), isNull(), anyInt())).thenReturn(List.of());

        service.list(filter, null, 25);

        org.mockito.Mockito.verify(repository).query(eq(filter), isNull(), anyInt());
    }

    @Test
    void filterByTargetEntity() {
        var targetId = UUID.randomUUID();
        var filter   = new AuditLogQueryFilter(null, null, targetId, "WALLET", null, null, null);
        when(repository.query(eq(filter), isNull(), anyInt())).thenReturn(List.of());

        service.list(filter, null, 25);

        org.mockito.Mockito.verify(repository).query(eq(filter), isNull(), anyInt());
    }

    @Test
    void limitClampedToMax() {
        when(repository.query(eq(EMPTY_FILTER), isNull(), eq(101))).thenReturn(List.of());

        service.list(EMPTY_FILTER, null, 500);

        org.mockito.Mockito.verify(repository).query(eq(EMPTY_FILTER), isNull(), eq(101));
    }

    @Test
    void nullLimitDefaultsTo25() {
        when(repository.query(eq(EMPTY_FILTER), isNull(), eq(26))).thenReturn(List.of());

        service.list(EMPTY_FILTER, null, null);

        org.mockito.Mockito.verify(repository).query(eq(EMPTY_FILTER), isNull(), eq(26));
    }
}
