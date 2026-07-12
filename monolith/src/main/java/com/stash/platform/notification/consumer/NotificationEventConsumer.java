package com.stash.platform.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.platform.notification.config.NotificationRabbitConfig;
import com.stash.platform.notification.event.EventEnvelope;
import com.stash.platform.notification.service.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

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
        // This queue is bound with a "#" wildcard across every exchange the
        // platform publishes to (NotificationRabbitConfig), so it receives
        // plenty of traffic that was never meant for it — e.g. payments-service's
        // internal outbox events (OutboxRelay) and monolith's own
        // UserCreatedEventPublisher, both of which put event_id/event_type only
        // in AMQP headers, not in a body envelope (their actual consumers —
        // UserCreatedEventConsumer et al — read the body as the raw payload
        // directly, so that shape can't change here). Reading event_id/event_type
        // from headers first, with the body as a fallback, means this listener
        // no longer crashes on those unrelated event shapes; anything without a
        // registered NotificationTemplateRegistry entry already no-ops harmlessly
        // in NotificationDispatchService.handle().
        JsonNode root = objectMapper.readTree(message.getBody());
        var props = message.getMessageProperties();

        String eventId = headerOrBodyText(props, root, "event_id");
        String eventType = headerOrBodyText(props, root, "event_type");
        if (eventId == null || eventType == null) {
            log.debug("Dropping message with no event_id/event_type in headers or body — " +
                      "not a platform notification event. routingKey={}", props.getReceivedRoutingKey());
            return;
        }

        String correlationId = headerOrBodyText(props, root, "correlation_id");
        Instant occurredAt = root.hasNonNull("occurred_at")
                ? Instant.parse(root.get("occurred_at").asText())
                : Instant.now();
        JsonNode payload = root.hasNonNull("payload") ? root.get("payload") : root;

        var envelope = new EventEnvelope(
                eventId,
                eventType,
                root.path("schema_version").asText("1.0"),
                root.path("source_service").asText("unknown"),
                occurredAt,
                correlationId,
                payload);

        dispatchService.handle(envelope);
    }

    private static String headerOrBodyText(MessageProperties props, JsonNode root, String field) {
        Object header = props.getHeaders().get(field);
        if (header != null) {
            return header.toString();
        }
        return root.hasNonNull(field) ? root.get(field).asText() : null;
    }
}
