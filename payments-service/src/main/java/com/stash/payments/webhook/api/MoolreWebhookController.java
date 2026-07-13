package com.stash.payments.webhook.api;

import com.stash.payments.webhook.service.MoolreWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Receives inbound Moolre payment/transfer callback events.
 *
 * <p>Returns 200 on success and on duplicate deliveries. Returns 400 only
 * when shared-secret verification fails.
 *
 * <p>Not behind JWT — Moolre has no JWT. Verification inside
 * {@link MoolreWebhookService} is the sole auth mechanism.
 */
@RestController
@RequestMapping("/webhooks")
public class MoolreWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MoolreWebhookController.class);

    private final MoolreWebhookService webhookService;

    public MoolreWebhookController(MoolreWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/moolre")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Moolre-Secret", required = false) String secret,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        log.info("Webhook received from Moolre: correlationId={}", correlationId);

        boolean accepted = webhookService.process(rawBody, secret, correlationId);

        return accepted
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
