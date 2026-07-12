package com.stash.challenge.event;

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
import java.util.UUID;

@Component
public class ChallengeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChallengeEventPublisher.class);
    private static final String EXCHANGE    = "monolith.events";
    private static final String ROUTING_KEY = "challenge.completed";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper   objectMapper;

    public ChallengeEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper   = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChallengeCompleted(ChallengeCompletedEvent event) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "event_id",           event.userChallengeId().toString(),
                    "event_type",         "ChallengeCompleted",
                    "schema_version",     "1.0",
                    "source_service",     "monolith",
                    "occurred_at",        event.occurredAt().toString(),
                    "payload", Map.of(
                            "user_challenge_id", event.userChallengeId().toString(),
                            "user_id",           event.userId().toString(),
                            "challenge_id",      event.challengeId().toString()
                    )
            ));
            rabbitTemplate.send(EXCHANGE, ROUTING_KEY,
                    MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                            .setHeader("event_id", UUID.randomUUID().toString())
                            .setHeader("event_type", ROUTING_KEY)
                            .build());
            log.info("Published challenge.completed userChallengeId={} userId={}",
                    event.userChallengeId(), event.userId());
        } catch (Exception e) {
            log.error("Failed to publish challenge.completed userChallengeId={}: {}",
                    event.userChallengeId(), e.getMessage());
        }
    }
}
