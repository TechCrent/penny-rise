package com.stash.payments.outbox.service;

import com.stash.payments.outbox.domain.OutboxDeadLetter;
import com.stash.payments.outbox.domain.OutboxEventEntity;
import com.stash.payments.outbox.domain.OutboxEventStatus;
import com.stash.payments.outbox.metrics.OutboxMetrics;
import com.stash.payments.outbox.repository.OutboxDeadLetterRepository;
import com.stash.payments.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final OutboxEventRepository      outboxRepo  = Mockito.mock(OutboxEventRepository.class);
    private final OutboxDeadLetterRepository dlRepo      = Mockito.mock(OutboxDeadLetterRepository.class);
    private final RabbitTemplate             rabbit      = Mockito.mock(RabbitTemplate.class);
    private final OutboxMetrics              metrics     = Mockito.mock(OutboxMetrics.class);
    private final OutboxRelay relay =
            new OutboxRelay(outboxRepo, dlRepo, rabbit, metrics, FIXED_CLOCK);

    @BeforeEach
    void setUp() {
        when(outboxRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dlRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Default: no pending rows → lag = 0
        when(outboxRepo.findOldestPendingCreatedAt()).thenReturn(null);
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy path: event published and marked SENT")
    void publishes_and_marks_sent() {
        OutboxEventEntity event = pendingEvent("payments.deposit.completed");
        when(outboxRepo.findPendingBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(event));

        relay.relay();

        verify(rabbit).send(eq("payments.events"), eq("deposit.completed"), any(Message.class));
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();
        verify(metrics).recordPublished();
    }

    @Test
    @DisplayName("empty batch: no publish attempts, pending count set to 0")
    void empty_batch_is_noop() {
        when(outboxRepo.findPendingBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of());

        relay.relay();

        verifyNoInteractions(rabbit);
        verify(metrics).updatePendingCount(0);
    }

    // ── Retry path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("RabbitMQ failure increments attempts and stays PENDING")
    void publish_failure_increments_attempts() {
        OutboxEventEntity event = pendingEvent("payments.deposit.completed");
        when(outboxRepo.findPendingBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(event));
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbit).send(any(String.class), any(String.class), any(Message.class));

        relay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastAttemptedAt()).isNotNull();
        verify(metrics).recordFailed();
        verifyNoInteractions(dlRepo);
    }

    @Test
    @DisplayName("failure on attempt 4 of 5 stays PENDING (not dead-lettered yet)")
    void failure_on_attempt_four_stays_pending() {
        OutboxEventEntity event = pendingEventWithAttempts("payments.deposit.completed", 3);
        when(outboxRepo.findPendingBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(event));
        doThrow(new AmqpException("still down")).when(rabbit).send(any(String.class), any(String.class), any(Message.class));

        relay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        verifyNoInteractions(dlRepo);
    }

    // ── Dead-letter path ──────────────────────────────────────────────────

    @Test
    @DisplayName("max attempts (5) reached: row marked FAILED and moved to dead-letter")
    void max_attempts_triggers_dead_letter() {
        OutboxEventEntity event = pendingEventWithAttempts("payments.deposit.completed", 4);
        when(outboxRepo.findPendingBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(event));
        doThrow(new AmqpException("still down")).when(rabbit).send(any(String.class), any(String.class), any(Message.class));

        relay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);

        ArgumentCaptor<OutboxDeadLetter> dlCaptor = ArgumentCaptor.forClass(OutboxDeadLetter.class);
        verify(dlRepo).save(dlCaptor.capture());
        assertThat(dlCaptor.getValue().getOriginalEventId()).isEqualTo(event.getId());
        assertThat(dlCaptor.getValue().getFailedReason()).contains("still down");
        verify(metrics).recordDeadLettered();
    }

    // ── Exchange derivation ───────────────────────────────────────────────

    @Test
    @DisplayName("exchange derived correctly from event_type")
    void exchange_derivation() {
        assertThat(OutboxRelay.deriveExchange("payments.deposit.completed"))
                .isEqualTo("payments.events");
        assertThat(OutboxRelay.deriveExchange("kyc.submission.approved"))
                .isEqualTo("kyc.events");
        assertThat(OutboxRelay.deriveExchange("vault.early_exit.initiated"))
                .isEqualTo("vault.events");
    }

    // ── Metrics ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("pending count metric updated on every poll cycle")
    void pending_count_metric_updated() {
        OutboxEventEntity event = pendingEvent("payments.deposit.completed");
        when(outboxRepo.findPendingBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(event));

        relay.relay();

        verify(metrics).updatePendingCount(1);
    }

    // ── Lag metric ────────────────────────────────────────────────────────

    @Test
    @DisplayName("relay lag metric updated from oldest PENDING row age")
    void relay_lag_metric_updated() {
        OutboxEventEntity event = pendingEvent("payments.deposit.completed");
        when(outboxRepo.findPendingBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(event));
        when(outboxRepo.findOldestPendingCreatedAt())
                .thenReturn(Instant.parse("2026-06-24T09:59:30Z")); // 30 seconds before FIXED_CLOCK

        relay.relay();

        verify(metrics).updateRelayLagSeconds(30L);
    }

    @Test
    @DisplayName("relay lag is zero when no PENDING rows exist")
    void relay_lag_zero_when_empty() {
        when(outboxRepo.findPendingBatch(OutboxRelay.BATCH_SIZE)).thenReturn(List.of());
        when(outboxRepo.findOldestPendingCreatedAt()).thenReturn(null);

        relay.relay();

        verify(metrics).updateRelayLagSeconds(0L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static OutboxEventEntity pendingEvent(String eventType) {
        return pendingEventWithAttempts(eventType, 0);
    }

    private static OutboxEventEntity pendingEventWithAttempts(String eventType, int attempts) {
        String routingKey = eventType.substring(eventType.indexOf('.') + 1);
        OutboxEventEntity entity = new OutboxEventEntity(
                eventType,
                "1.0",
                "VAULT_DEPOSIT",
                UUID.randomUUID(),
                "{\"test\":true}",
                routingKey,
                "corr-test-001",
                Instant.parse("2026-06-24T09:00:00Z")
        );
        for (int i = 0; i < attempts; i++) {
            entity.incrementAttempt(Instant.parse("2026-06-24T09:00:00Z"));
        }
        return entity;
    }
}
