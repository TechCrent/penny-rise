package com.stash.platform.susu.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Publishes susu lifecycle events to RabbitMQ AFTER the activation transaction
 * commits. Uses the same AFTER_COMMIT pattern as VaultUnlockedEventPublisher.
 *
 * <p>Not at-least-once — if the JVM crashes between commit and publish, the
 * events are lost. The monolith outbox (v0.5) will close this gap.
 */
@Component
public class SusuEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SusuEventPublisher.class);
    private static final String EXCHANGE = "susu.events";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   objectMapper;

    public SusuEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper   = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSusuActivated(SusuActivatedEvent event) {
        publish("susu.group.activated", Map.of(
                "group_id",          event.getGroupId().toString(),
                "group_name",        event.getGroupName(),
                "organiser_user_id", event.getOrganiserUserId().toString(),
                "member_user_ids",   event.getMemberUserIds().stream()
                        .map(Object::toString).toList(),
                "ledger_account_id", event.getLedgerAccountId().toString(),
                "activated_at",      event.getActivatedAt().toString(),
                "event_type",        "susu.group.activated"
        ), event.getCorrelationId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSusuRoundStarted(SusuRoundStartedEvent event) {
        publish("susu.round.started", Map.of(
                "group_id",                  event.getGroupId().toString(),
                "round_id",                  event.getRoundId().toString(),
                "round_number",              event.getRoundNumber(),
                "total_rounds",              event.getTotalRounds(),
                "recipient_user_id",         event.getRecipientUserId().toString(),
                "scheduled_collection_at",   event.getScheduledCollectionAt().toString(),
                "event_type",                "susu.round.started"
        ), event.getCorrelationId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoundCompleted(SusuRoundCompletedEvent event) {
        publish("susu.round.completed", Map.of(
                "group_id",                    event.getGroupId().toString(),
                "round_id",                    event.getRoundId().toString(),
                "round_number",                event.getRoundNumber(),
                "total_rounds",                event.getTotalRounds(),
                "recipient_user_id",           event.getRecipientUserId().toString(),
                "disbursed_amount",            event.getDisbursedAmount(),
                "disbursement_transaction_id", event.getDisbursementTransactionId().toString(),
                "occurred_at",                 event.getOccurredAt().toString(),
                "event_type",                  "susu.round.completed"
        ), event.getCorrelationId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupCompleted(SusuGroupCompletedEvent event) {
        publish("susu.group.completed", Map.of(
                "group_id",          event.getGroupId().toString(),
                "group_name",        event.getGroupName(),
                "organiser_user_id", event.getOrganiserUserId().toString(),
                "total_rounds",      event.getTotalRounds(),
                "occurred_at",       event.getOccurredAt().toString(),
                "event_type",        "susu.group.completed"
        ), event.getCorrelationId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberLeft(SusuMemberLeftEvent event) {
        publish("susu.member.left", Map.of(
                "group_id",        event.getGroupId().toString(),
                "user_id",         event.getUserId().toString(),
                "reason",          event.getReason(),
                "group_status",    event.getGroupStatus(),
                "group_cancelled", event.isGroupCancelled(),
                "occurred_at",     event.getOccurredAt().toString(),
                "event_type",      "susu.member.left"
        ), event.getCorrelationId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoundSkipped(SusuRoundSkippedEvent event) {
        publish("susu.round.skipped", Map.of(
                "group_id",                   event.getGroupId().toString(),
                "round_id",                   event.getRoundId().toString(),
                "round_number",               event.getRoundNumber(),
                "original_recipient_user_id", event.getOriginalRecipientUserId().toString(),
                "skipped_pot_amount",         event.getSkippedPotAmount(),
                "occurred_at",                event.getOccurredAt().toString(),
                "event_type",                 "susu.round.skipped"
        ), event.getCorrelationId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributionReminder(SusuContributionReminderEvent event) {
        publish("susu.contribution.reminder", Map.of(
                "group_id",        event.getGroupId().toString(),
                "round_id",        event.getRoundId().toString(),
                "contribution_id", event.getContributionId().toString(),
                "member_user_id",  event.getMemberUserId().toString(),
                "reminder_type",   event.getReminderType(),
                "amount_pesewas",  event.getAmountPesewas(),
                "due_date",        event.getDueDate().toString(),
                "emitted_at",      event.getEmittedAt().toString(),
                "event_type",      "susu.contribution.reminder"
        ), event.getCorrelationId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContributionLate(SusuContributionLateEvent event) {
        publish("susu.contribution.late", Map.of(
                "group_id",        event.getGroupId().toString(),
                "round_id",        event.getRoundId().toString(),
                "contribution_id", event.getContributionId().toString(),
                "member_user_id",  event.getMemberUserId().toString(),
                "penalty_amount",  event.getPenaltyAmount(),
                "penalty_waived",  event.isPenaltyWaived(),
                "occurred_at",     event.getOccurredAt().toString(),
                "event_type",      "susu.contribution.late"
        ), event.getCorrelationId());
    }

    private void publish(String routingKey, Map<String, Object> payload, String correlationId) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            rabbitTemplate.send(EXCHANGE, routingKey,
                    MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                            .setHeader("correlation_id", correlationId)
                            .setHeader("event_type", routingKey)
                            .build());
            log.info("Published {}: group={} correlation={}",
                    routingKey, payload.get("group_id"), correlationId);
        } catch (Exception e) {
            log.error("Failed to publish {} correlation={}: {}",
                    routingKey, correlationId, e.getMessage());
        }
    }
}