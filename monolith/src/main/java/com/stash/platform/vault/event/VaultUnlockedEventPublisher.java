package com.stash.platform.vault.event;

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
 * Publishes vault.unlocked to RabbitMQ AFTER the unlock transaction commits.
 *
 * <p>Same pattern as UserCreatedEventPublisher — not at-least-once until
 * the monolith gets its own outbox (v0.5). A publish failure is logged
 * but does not roll back the unlock (the vault is already unlocked).
 */
@Component
public class VaultUnlockedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(VaultUnlockedEventPublisher.class);
    private static final String EXCHANGE    = "vault.events";
    private static final String ROUTING_KEY = "vault.unlocked";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   objectMapper;

    public VaultUnlockedEventPublisher(RabbitTemplate rabbitTemplate,
                                        ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper   = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVaultUnlocked(VaultUnlockedApplicationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "vault_id",       event.getVaultId().toString(),
                    "owner_user_id",  event.getOwnerUserId().toString(),
                    "unlocked_at",    event.getUnlockedAt().toString(),
                    "event_type",     "vault.unlocked"
            ));

            var message = MessageBuilder
                    .withBody(payload.getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setHeader("correlation_id",  event.getCorrelationId())
                    .setHeader("event_type",      "vault.unlocked")
                    .build();

            rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message);
            log.info("Published vault.unlocked: vaultId={} correlation={}",
                    event.getVaultId(), event.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to publish vault.unlocked for vaultId={} correlation={}: {}",
                    event.getVaultId(), event.getCorrelationId(), e.getMessage());
        }
    }
}
