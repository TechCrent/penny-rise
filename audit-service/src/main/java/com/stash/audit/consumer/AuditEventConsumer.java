package com.stash.audit.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.audit.service.AuditIngestService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.stash.audit.config.AuditMessagingConfig.QUEUE_NAME;

@Component
public class AuditEventConsumer {

    private final AuditIngestService ingestService;
    private final ObjectMapper objectMapper;

    public AuditEventConsumer(AuditIngestService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper  = objectMapper;
    }

    /**
     * Parses OutboxRelay's actual wire format: event_id/event_type/correlation_id
     * are custom message headers (see OutboxRelay#publish), not the AMQP
     * messageId/type/correlationId properties — those are never set by any
     * publisher, so reading them here always returned null and rejected every
     * message to the DLQ. body=raw payload JSON.
     *
     * Exceptions propagate to the RetryOperationsInterceptor (5-attempt
     * exponential backoff) and ultimately to the DLQ.
     */
    @RabbitListener(queues = QUEUE_NAME)
    public void onMessage(Message message) throws Exception {
        var props = message.getMessageProperties();

        String eventId    = (String) props.getHeaders().get("event_id");
        String eventType  = (String) props.getHeaders().get("event_type");
        String correlationId = (String) props.getHeaders().get("correlation_id");
        String sourceService = exchangeToServiceName(props.getReceivedExchange());

        if (eventId == null || eventType == null) {
            throw new IllegalArgumentException(
                    "Message missing required AMQP properties messageId/type — cannot process; exchange="
                    + props.getReceivedExchange() + " routingKey=" + props.getReceivedRoutingKey());
        }

        JsonNode payload = objectMapper.readTree(message.getBody());

        ingestService.ingest(new IncomingAuditEvent(eventId, eventType, sourceService, correlationId, payload));
    }

    private String exchangeToServiceName(String exchangeName) {
        // "payments.events" → "payments"
        return (exchangeName != null && exchangeName.endsWith(".events"))
                ? exchangeName.substring(0, exchangeName.length() - ".events".length())
                : "unknown";
    }
}
