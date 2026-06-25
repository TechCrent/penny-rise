package com.stash.payments.paystack.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.paystack.service.PaystackSubaccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes {@code paystack.subaccount.provision.requested} events from
 * the Payments Service's own outbox relay and calls Paystack to create
 * the subaccount.
 *
 * <p>This is an intra-service event — the Payments Service both publishes
 * (via outbox) and consumes this event. The outbox relay guarantees
 * at-least-once delivery; this consumer is idempotent.
 *
 * <p><strong>Retry:</strong> if Paystack fails, the consumer throws, which
 * causes RabbitMQ to requeue. After the configured max-retries the message
 * goes to {@code payments.paystack.subaccount.provision.dlq}. Ops investigates.
 *
 * <p><strong>Queue:</strong> {@code payments.paystack.subaccount.provision.queue}
 * bound to {@code payments.events} with routing key
 * {@code paystack.subaccount.provision.requested}.
 */
@Component
public class PaystackSubaccountProvisionConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(PaystackSubaccountProvisionConsumer.class);

    private final PaystackSubaccountService subaccountService;
    private final ObjectMapper              objectMapper;

    public PaystackSubaccountProvisionConsumer(PaystackSubaccountService subaccountService,
                                               ObjectMapper objectMapper) {
        this.subaccountService = subaccountService;
        this.objectMapper      = objectMapper;
    }

    @RabbitListener(queues = "payments.paystack.subaccount.provision.queue")
    public void onProvisionRequested(Message message) {
        String correlationId = extractCorrelationId(message);
        String body = new String(message.getBody());

        log.debug("Received paystack.subaccount.provision.requested. correlation={}",
                correlationId);

        try {
            Map<?, ?> payload = objectMapper.readValue(body, Map.class);
            UUID ledgerAccountId = UUID.fromString((String) payload.get("ledgerAccountId"));
            UUID userId          = UUID.fromString((String) payload.get("userId"));
            String userEmail     = (String) payload.get("userEmail");

            subaccountService.provisionSubaccount(
                    ledgerAccountId, userId, userEmail,
                    correlationId != null ? correlationId : "unknown");

        } catch (Exception e) {
            log.error("Failed to provision Paystack subaccount. correlation={} error={}",
                    correlationId, e.getMessage());
            // Re-throw: RabbitMQ requeues; after max-retries goes to DLQ
            throw new RuntimeException("Paystack subaccount provision failed", e);
        }
    }

    private String extractCorrelationId(Message message) {
        Object header = message.getMessageProperties().getHeaders().get("correlation_id");
        return header != null ? header.toString() : null;
    }
}
