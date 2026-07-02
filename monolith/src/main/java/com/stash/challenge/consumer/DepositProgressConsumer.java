package com.stash.challenge.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.challenge.config.ChallengeRabbitConfig;
import com.stash.challenge.service.ChallengeProgressService;
import com.stash.platform.notification.event.EventEnvelope;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DepositProgressConsumer {

    private final ChallengeProgressService progressService;
    private final ObjectMapper             objectMapper;

    public DepositProgressConsumer(ChallengeProgressService progressService, ObjectMapper objectMapper) {
        this.progressService = progressService;
        this.objectMapper    = objectMapper;
    }

    @RabbitListener(
            queues           = ChallengeRabbitConfig.QUEUE_NAME,
            containerFactory = "challengeListenerContainerFactory")
    public void onMessage(Message message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getBody());

        var envelope = new EventEnvelope(
                root.get("event_id").asText(),
                root.get("event_type").asText(),
                root.path("schema_version").asText("1.0"),
                root.path("source_service").asText("unknown"),
                Instant.parse(root.get("occurred_at").asText()),
                root.path("correlation_id").asText(null),
                root.get("payload"));

        progressService.handleDeposit(envelope);
    }
}
