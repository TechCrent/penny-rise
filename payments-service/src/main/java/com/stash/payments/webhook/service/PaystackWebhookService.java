package com.stash.payments.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.transaction.service.WithdrawalService;
import com.stash.payments.webhook.domain.ProcessedWebhookEventEntity;
import com.stash.payments.webhook.repository.ProcessedWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates Paystack webhook processing with HMAC verification,
 * deduplication, routing, and transactional ledger writes.
 *
 * <p>Returns {@code true} if the webhook was processed (or is a recognised
 * duplicate), {@code false} if the HMAC signature is invalid. The controller
 * maps these to HTTP 200 and HTTP 400 respectively.
 *
 * <p>On handler failure the exception is re-thrown so that the transaction
 * rolls back (including the dedup INSERT) — giving Paystack a clean retry.
 */
@Service
public class PaystackWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaystackWebhookService.class);

    private final PaystackWebhookVerifier         verifier;
    private final ProcessedWebhookEventRepository webhookEventRepo;
    private final ChargeSuccessHandler            chargeSuccessHandler;
    private final WithdrawalService               withdrawalService;
    private final ObjectMapper                    objectMapper;
    private final Clock                           clock;

    public PaystackWebhookService(PaystackWebhookVerifier verifier,
                                  ProcessedWebhookEventRepository webhookEventRepo,
                                  ChargeSuccessHandler chargeSuccessHandler,
                                  WithdrawalService withdrawalService,
                                  ObjectMapper objectMapper,
                                  Clock clock) {
        this.verifier             = verifier;
        this.webhookEventRepo     = webhookEventRepo;
        this.chargeSuccessHandler = chargeSuccessHandler;
        this.withdrawalService    = withdrawalService;
        this.objectMapper         = objectMapper;
        this.clock                = clock;
    }

    /**
     * @return {@code true} on success or recognised duplicate;
     *         {@code false} if HMAC is invalid
     */
    @Transactional
    public boolean process(byte[] rawBody, String signature, String correlationId) {
        if (!verifier.verify(rawBody, signature)) {
            log.warn("Webhook rejected: invalid HMAC. correlationId={}", correlationId);
            return false;
        }

        try {
            JsonNode root      = objectMapper.readTree(rawBody);
            String eventId     = root.path("id").asText(null);
            if (eventId == null) {
                eventId = root.path("data").path("id").asText(java.util.UUID.randomUUID().toString());
            }
            String eventType   = root.path("event").asText("unknown");
            String payloadHash = sha256hex(rawBody);

            ProcessedWebhookEventEntity dedupRow = new ProcessedWebhookEventEntity(
                    "PAYSTACK", eventId, eventType, payloadHash,
                    true, correlationId, Instant.now(clock));

            try {
                webhookEventRepo.saveAndFlush(dedupRow);
            } catch (DataIntegrityViolationException e) {
                log.info("Duplicate webhook delivery ignored: provider=PAYSTACK eventId={} type={} correlationId={}",
                        eventId, eventType, correlationId);
                return true;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.convertValue(root.path("data"), Map.class);
            UUID resultingTransactionId = route(eventType, data, correlationId);

            dedupRow.markCompleted(resultingTransactionId, Instant.now(clock));
            webhookEventRepo.save(dedupRow);

            log.info("Webhook processed: type={} eventId={} correlationId={}",
                    eventType, eventId, correlationId);
            return true;

        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Webhook handler failed: correlationId={}", correlationId, e);
            throw new RuntimeException("Webhook processing failed", e);
        }
    }

    private UUID route(String eventType, Map<String, Object> data, String correlationId) {
        return switch (eventType) {
            case "charge.success" -> chargeSuccessHandler.handle(data, correlationId);
            case "transfer.success" -> {
                String transferCode = (String) data.get("transfer_code");
                long   amount       = toLong(data.get("amount"));
                yield withdrawalService.handleTransferSuccess(transferCode, amount, correlationId);
            }
            case "transfer.failed" -> {
                String transferCode   = (String) data.get("transfer_code");
                String failureReason  = (String) data.getOrDefault("gateway_response", "Unknown failure");
                yield withdrawalService.handleTransferFailed(transferCode, failureReason, correlationId);
            }
            default -> {
                log.info("Unknown webhook event type={} — ignoring. correlationId={}",
                        eventType, correlationId);
                yield null;
            }
        };
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static String sha256hex(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
