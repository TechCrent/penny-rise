package com.stash.platform.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.platform.notification.config.NotificationRabbitConfig;
import com.stash.platform.notification.event.EventEnvelope;
import com.stash.platform.notification.service.NotificationDispatchService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class NotificationEventConsumer {

    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(NotificationDispatchService dispatchService, ObjectMapper objectMapper) {
        this.dispatchService = dispatchService;
        this.objectMapper    = objectMapper;
    }

    @RabbitListener(
            queues          = NotificationRabbitConfig.QUEUE_NAME,
            containerFactory = "notificationListenerContainerFactory")
    public void onMessage(Message message) throws Exception {
        // Parse the REAL envelope shape from the message body per System Design §4.2.
        // Corrects v0.5-009's assumption that event_id/event_type/correlation_id
        // were AMQP message properties.
        JsonNode root = objectMapper.readTree(message.getBody());

        var envelope = new EventEnvelope(
                root.get("event_id").asText(),
                root.get("event_type").asText(),
                root.path("schema_version").asText("1.0"),
                root.path("source_service").asText("unknown"),
                Instant.parse(root.get("occurred_at").asText()),
                root.path("correlation_id").asText(null),
                root.get("payload"));

        dispatchService.handle(envelope);
    }
}
