package com.stash.platform.user.event;

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
import java.util.UUID;

/**
 * Publishes a user.created event to RabbitMQ after the user row commits.
 *
 * <p>Uses {@code @TransactionalEventListener(AFTER_COMMIT)} so the publish never
 * happens if the signup transaction rolls back. The publish is {@code @Async} to
 * avoid blocking the signup HTTP response.
 *
 * <p><strong>Known limitation:</strong> this is NOT at-least-once delivery.
 * If the service crashes between AFTER_COMMIT and the RabbitMQ publish, the
 * event is lost. The monolith needs its own outbox (v0.5) for full reliability.
 * For v0.3 internal testing this is acceptable.
 */
@Component
public class UserCreatedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserCreatedEventPublisher.class);

    private static final String EXCHANGE    = "user.events";
    private static final String ROUTING_KEY = "user.created";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   objectMapper;

    public UserCreatedEventPublisher(RabbitTemplate rabbitTemplate,
                                     ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper   = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedApplicationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(new UserCreatedEventPayload(
                    event.getUserId(), event.getEmail(), event.getCorrelationId()));

            var message = MessageBuilder
                    .withBody(payload.getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setHeader("correlation_id", event.getCorrelationId())
                    .setHeader("event_type", "user.created")
                    .build();

            rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message);

            log.info("Published user.created: userId={} correlation={}",
                    event.getUserId(), event.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to publish user.created for userId={} — " +
                      "USER_WALLET will not be provisioned until retry. " +
                      "correlation={} error={}",
                      event.getUserId(), event.getCorrelationId(), e.getMessage());
        }
    }

    record UserCreatedEventPayload(UUID userId, String email, String correlationId) {}
}
