package com.stash.payments.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.consumer.event.UserCreatedEvent;
import com.stash.payments.ledger.service.UserWalletProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code user.created} events from the monolith and provisions
 * a USER_WALLET ledger account for each new user.
 *
 * <p><strong>Idempotency:</strong> {@link UserWalletProvisioningService#provisionWallet}
 * is a no-op if the account already exists. This consumer acknowledges
 * the message in both cases — no requeue loop.
 *
 * <p><strong>Dead-lettering:</strong> if the message cannot be deserialised
 * (malformed JSON, missing required fields), the exception propagates and
 * Spring AMQP's dead-letter configuration routes the message to
 * {@code payments.user.created.dlq}. The consumer does NOT catch and swallow
 * deserialization errors — we want visibility of malformed events.
 *
 * <p><strong>Queue:</strong> {@code payments.user.created.queue}
 * bound to exchange {@code user.events} with routing key {@code user.created}.
 */
@Component
public class UserCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserCreatedEventConsumer.class);

    private final UserWalletProvisioningService provisioningService;
    private final ObjectMapper objectMapper;

    public UserCreatedEventConsumer(UserWalletProvisioningService provisioningService,
                                    ObjectMapper objectMapper) {
        this.provisioningService = provisioningService;
        this.objectMapper        = objectMapper;
    }

    @RabbitListener(queues = "payments.user.created.queue")
    public void onUserCreated(Message message) {
        String correlationId = extractCorrelationId(message);
        String body = new String(message.getBody());

        log.debug("Received user.created event: correlation={}", correlationId);

        UserCreatedEvent event;
        try {
            event = objectMapper.readValue(body, UserCreatedEvent.class);
        } catch (Exception e) {
            log.error("Malformed user.created event — dead-lettering. " +
                      "correlation={} body={} error={}",
                      correlationId, body, e.getMessage());
            throw new RuntimeException("Malformed user.created event", e);
        }

        if (event.userId() == null) {
            log.error("user.created event missing userId — dead-lettering. correlation={}",
                      correlationId);
            throw new IllegalArgumentException("user.created event missing userId");
        }

        provisioningService.provisionWallet(event.userId(),
                correlationId != null ? correlationId : event.correlationId());

        log.info("user.created processed: userId={} correlation={}", event.userId(), correlationId);
    }

    private String extractCorrelationId(Message message) {
        var props = message.getMessageProperties();
        Object header = props.getHeaders().get("correlation_id");
        return header != null ? header.toString()
                : (props.getCorrelationId() != null ? props.getCorrelationId() : null);
    }
}
