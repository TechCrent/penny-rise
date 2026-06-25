package com.stash.payments.webhook.api;

import com.stash.payments.webhook.service.PaystackWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Receives inbound Paystack webhook events.
 *
 * <p>Paystack retries on any non-2xx response, so we return 200 on success
 * and on duplicate deliveries. We return 400 only for invalid HMAC — in which
 * case we don't want Paystack to retry (the event was not from Paystack).
 *
 * <p>This endpoint is NOT behind JWT auth — Paystack has no JWT. HMAC-SHA512
 * verification inside {@link PaystackWebhookService} is the sole auth
 * mechanism. The SecurityConfig explicitly permits this path.
 */
@RestController
@RequestMapping("/webhooks")
public class PaystackWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaystackWebhookController.class);

    private final PaystackWebhookService webhookService;

    public PaystackWebhookController(PaystackWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/paystack")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        log.info("Webhook received from Paystack: correlationId={}", correlationId);

        boolean accepted = webhookService.process(rawBody, signature, correlationId);

        return accepted
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
