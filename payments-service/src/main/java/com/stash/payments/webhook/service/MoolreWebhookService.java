package com.stash.payments.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.repository.TransactionRepository;
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
 * Orchestrates Moolre webhook processing with shared-secret verification,
 * deduplication, routing, and transactional ledger writes.
 *
 * <p>Moolre callback shape (documented):
 * {@code { status, code, message, data: { externalref, transactionid, amount, txstatus, ... } }}.
 * Lookup uses {@code data.externalref} (= our STSH reference).
 */
@Service
public class MoolreWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MoolreWebhookService.class);

    private final MoolreWebhookVerifier           verifier;
    private final ProcessedWebhookEventRepository webhookEventRepo;
    private final ChargeSuccessHandler            chargeSuccessHandler;
    private final WithdrawalService               withdrawalService;
    private final TransactionRepository           transactionRepo;
    private final ObjectMapper                    objectMapper;
    private final Clock                           clock;

    public MoolreWebhookService(MoolreWebhookVerifier verifier,
                                ProcessedWebhookEventRepository webhookEventRepo,
                                ChargeSuccessHandler chargeSuccessHandler,
                                WithdrawalService withdrawalService,
                                TransactionRepository transactionRepo,
                                ObjectMapper objectMapper,
                                Clock clock) {
        this.verifier             = verifier;
        this.webhookEventRepo     = webhookEventRepo;
        this.chargeSuccessHandler = chargeSuccessHandler;
        this.withdrawalService    = withdrawalService;
        this.transactionRepo      = transactionRepo;
        this.objectMapper         = objectMapper;
        this.clock                = clock;
    }

    /**
     * @return {@code true} on success or recognised duplicate;
     *         {@code false} if verification fails
     */
    @Transactional
    public boolean process(byte[] rawBody, String secretHeader, String correlationId) {
        if (!verifier.verify(rawBody, secretHeader)) {
            log.warn("Moolre webhook rejected: invalid secret. correlationId={}", correlationId);
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode dataNode = root.path("data");

            String externalRef = textOrNull(dataNode, "externalref");
            String transactionId = textOrNull(dataNode, "transactionid");
            String eventId = firstNonBlank(transactionId,
                    externalRef != null ? externalRef + "-" + textOrNull(dataNode, "ts") : null,
                    UUID.randomUUID().toString());

            int topStatus = root.path("status").asInt(-1);
            String code = root.path("code").asText("");
            int txstatus = dataNode.path("txstatus").asInt(-1);
            if (txstatus < 0 && topStatus == 1) {
                // Some callbacks only set top-level status=1 / code=P01
                txstatus = 1;
            }

            String eventType = resolveEventType(txstatus, code);
            String payloadHash = sha256hex(rawBody);

            ProcessedWebhookEventEntity dedupRow = new ProcessedWebhookEventEntity(
                    "MOOLRE", eventId, eventType, payloadHash,
                    true, correlationId, Instant.now(clock));

            try {
                webhookEventRepo.saveAndFlush(dedupRow);
            } catch (DataIntegrityViolationException e) {
                log.info("Duplicate Moolre webhook ignored: eventId={} type={} correlationId={}",
                        eventId, eventType, correlationId);
                return true;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.convertValue(dataNode, Map.class);
            if (externalRef != null) {
                data.putIfAbsent("externalref", externalRef);
            }

            UUID resultingTransactionId = route(txstatus, data, externalRef, correlationId);

            dedupRow.markCompleted(resultingTransactionId, Instant.now(clock));

            log.info("Moolre webhook processed: type={} eventId={} correlationId={}",
                    eventType, eventId, correlationId);
            return true;

        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Moolre webhook handler failed: correlationId={}", correlationId, e);
            throw new RuntimeException("Moolre webhook processing failed", e);
        }
    }

    private UUID route(int txstatus, Map<String, Object> data,
                       String externalRef, String correlationId) {
        if (externalRef == null || externalRef.isBlank()) {
            log.warn("Moolre webhook missing data.externalref — ignoring. correlationId={}",
                    correlationId);
            return null;
        }

        TransactionEntity txn = transactionRepo.findByReference(externalRef).orElse(null);
        if (txn == null) {
            log.warn("Moolre webhook: no transaction for externalref={} — ignoring", externalRef);
            return null;
        }

        String type = txn.getTransactionType();

        if (txstatus == 1) {
            if ("DEPOSIT".equals(type)) {
                return chargeSuccessHandler.handle(data, correlationId);
            }
            if ("WITHDRAWAL".equals(type)) {
                long amount = ChargeSuccessHandler.parseAmountToPesewas(
                        data.containsKey("amount") ? data.get("amount") : txn.getGrossAmount());
                return withdrawalService.handleTransferSuccess(externalRef, amount, correlationId);
            }
        } else if (txstatus == 2) {
            if ("WITHDRAWAL".equals(type)) {
                String reason = stringVal(data.get("message"));
                if (reason == null) reason = "Moolre transfer failed";
                return withdrawalService.handleTransferFailed(externalRef, reason, correlationId);
            }
            if ("DEPOSIT".equals(type) && "PENDING".equals(txn.getStatus())) {
                txn.markFailed(Instant.now(clock));
                transactionRepo.save(txn);
                log.info("Moolre webhook: deposit {} marked FAILED (txstatus=2)", externalRef);
                return txn.getId();
            }
        } else {
            log.info("Moolre webhook non-terminal txstatus={} for ref={} — ignoring",
                    txstatus, externalRef);
        }
        return null;
    }

    private static String resolveEventType(int txstatus, String code) {
        if (txstatus == 1 || "P01".equalsIgnoreCase(code)) return "moolre.success";
        if (txstatus == 2) return "moolre.failed";
        return "moolre." + (code != null && !code.isBlank() ? code : "unknown");
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) return null;
        String t = child.asText(null);
        return t != null && !t.isBlank() ? t : null;
    }

    private static String stringVal(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
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
