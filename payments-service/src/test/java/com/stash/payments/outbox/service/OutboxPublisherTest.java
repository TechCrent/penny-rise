package com.stash.payments.outbox.service;

import com.stash.payments.outbox.domain.OutboxEvent;
import com.stash.payments.outbox.domain.OutboxEventEntity;
import com.stash.payments.outbox.domain.OutboxEventStatus;
import com.stash.payments.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final OutboxEventRepository    repo       = Mockito.mock(OutboxEventRepository.class);
    private final OutboxPayloadSerializer  serializer = new OutboxPayloadSerializer(
            new com.fasterxml.jackson.databind.ObjectMapper());
    private final OutboxPublisher publisher =
            new OutboxPublisher(repo, serializer, FIXED_CLOCK);

    private static final String CORRELATION_ID = "corr-001";

    @Test
    @DisplayName("persists outbox row with PENDING status and correct fields")
    void publish_saves_pending_row() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestEvent event = new TestEvent(UUID.randomUUID());
        publisher.publish(event, CORRELATION_ID);

        ArgumentCaptor<OutboxEventEntity> captor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repo).save(captor.capture());

        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getSentAt()).isNull();
        assertThat(saved.getEventType()).isEqualTo("test.event.fired");
        assertThat(saved.getRoutingKey()).isEqualTo("event.fired");
        assertThat(saved.getAggregateType()).isEqualTo("TEST_AGGREGATE");
        assertThat(saved.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-06-24T10:00:00Z"));
        assertThat(saved.getPayload()).isNotBlank();
    }

    @Test
    @DisplayName("payload is serialised as JSON containing the event's fields")
    void payload_is_serialised_json() {
        UUID aggregateId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        publisher.publish(new TestEvent(aggregateId), CORRELATION_ID);

        ArgumentCaptor<OutboxEventEntity> captor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repo).save(captor.capture());

        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("aaaaaaaa-0000-0000-0000-000000000001");
    }

    @Test
    @DisplayName("schema version defaults to 1.0")
    void schema_version_is_default() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        publisher.publish(new TestEvent(UUID.randomUUID()), CORRELATION_ID);

        ArgumentCaptor<OutboxEventEntity> captor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repo).save(captor.capture());

        assertThat(captor.getValue().getSchemaVersion()).isEqualTo("1.0");
    }

    // ── Test event ────────────────────────────────────────────────────────

    record TestEvent(UUID id) implements OutboxEvent {
        @Override public String getEventType()     { return "test.event.fired"; }
        @Override public String getRoutingKey()    { return "event.fired"; }
        @Override public String getAggregateType() { return "TEST_AGGREGATE"; }
        @Override public UUID   getAggregateId()   { return id; }
    }
}
