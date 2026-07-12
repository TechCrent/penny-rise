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

import java.util.Map;
import java.util.UUID;

/**
 * Publishes susu.round.fully_collected to RabbitMQ AFTER the contribution
 * transaction commits — same pattern as VaultUnlockedEventPublisher.
 * A publish failure is logged but does not roll back the contribution payment.
 */
@Component
public class SusuRoundFullyCollectedEventPublisher {

    private static final Logger log         = LoggerFactory.getLogger(SusuRoundFullyCollectedEventPublisher.class);
    private static final String EXCHANGE    = "susu.events";
    private static final String ROUTING_KEY = "susu.round.fully_collected";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   objectMapper;

    public SusuRoundFullyCollectedEventPublisher(RabbitTemplate rabbitTemplate,
                                                  ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper   = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoundFullyCollected(SusuRoundFullyCollectedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "group_id",          event.getGroupId().toString(),
                    "round_id",          event.getRoundId().toString(),
                    "round_number",      event.getRoundNumber(),
                    "actual_pot_amount", event.getActualPotAmount(),
                    "recipient_user_id", event.getRecipientUserId().toString(),
                    "occurred_at",       event.getOccurredAt().toString(),
                    "event_type",        "susu.round.fully_collected"
            ));

            var message = MessageBuilder
                    .withBody(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setHeader("event_id",       UUID.randomUUID().toString())
                    .setHeader("correlation_id", event.getCorrelationId())
                    .setHeader("event_type",     "susu.round.fully_collected")
                    .build();

            rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message);
            log.info("Published susu.round.fully_collected: roundId={} groupId={} correlation={}",
                    event.getRoundId(), event.getGroupId(), event.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to publish susu.round.fully_collected for roundId={} correlation={}: {}",
                    event.getRoundId(), event.getCorrelationId(), e.getMessage());
        }
    }
}
