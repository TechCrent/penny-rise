package com.stash.payments.webhook.service;

import com.stash.payments.paystack.client.PaystackClient;
import com.stash.payments.paystack.dto.TransactionVerifyResponse;
import com.stash.payments.transaction.domain.TransactionEntity;
import com.stash.payments.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Self-heals deposits stuck PENDING because Paystack's {@code charge.success}
 * webhook was missed, delayed, or (in local dev, with no public callback URL)
 * never had anywhere to land. Calls Paystack's real Verify Transaction API —
 * the same one {@link PaystackWebhookService}/{@link ChargeSuccessHandler}
 * would have processed a webhook through — so a deposit is never marked
 * COMPLETED on our say-so alone, only on Paystack's.
 *
 * <p>The {@code pendingThreshold} delay (default 60s) exists so this doesn't
 * race the real webhook on every ordinary deposit — most deposits complete
 * via webhook well within that window and are never touched here.
 */
@Service
public class DepositReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(DepositReconciliationService.class);

    private final TransactionRepository transactionRepo;
    private final PaystackClient paystackClient;
    private final ChargeSuccessHandler chargeSuccessHandler;
    private final Clock clock;
    private final Duration pendingThreshold;

    public DepositReconciliationService(
            TransactionRepository transactionRepo,
            PaystackClient paystackClient,
            ChargeSuccessHandler chargeSuccessHandler,
            Clock clock,
            @Value("${stash.paystack.reconciliation.pending-threshold-seconds:60}")
            long pendingThresholdSeconds) {
        this.transactionRepo = transactionRepo;
        this.paystackClient = paystackClient;
        this.chargeSuccessHandler = chargeSuccessHandler;
        this.clock = clock;
        this.pendingThreshold = Duration.ofSeconds(pendingThresholdSeconds);
    }

    /**
     * References of stale PENDING deposits worth verifying with Paystack.
     * Read-only — safe to call outside a write transaction.
     */
    public List<String> findStaleDepositReferences() {
        Instant threshold = Instant.now(clock).minus(pendingThreshold);
        return transactionRepo.findStalePendingDepositReferences(threshold);
    }

    /**
     * Verifies one deposit against Paystack and completes/fails it if
     * Paystack reports a terminal outcome. No-ops if the transaction has
     * already been resolved (e.g. the real webhook arrived first).
     *
     * <p>Own transaction boundary per reference — one failed/erroring
     * verification must not roll back others reconciled in the same job run.
     */
    @Transactional
    public void reconcileOne(String reference) {
        TransactionEntity txn = transactionRepo.findByReference(reference).orElse(null);
        if (txn == null || !"PENDING".equals(txn.getStatus()) || txn.getExternalReference() == null) {
            return;
        }

        TransactionVerifyResponse response;
        try {
            response = paystackClient.verifyTransaction(txn.getExternalReference());
        } catch (Exception e) {
            log.warn("DepositReconciliationService: verifyTransaction failed for ref={} paystackRef={}: {}",
                    reference, txn.getExternalReference(), e.getMessage());
            return;
        }

        String status = response.data() != null ? response.data().status() : null;

        if ("success".equalsIgnoreCase(status)) {
            Map<String, Object> data = Map.of(
                    "reference", response.data().reference(),
                    "amount", response.data().amount()
            );
            chargeSuccessHandler.handle(data, txn.getCorrelationId());
            log.info("DepositReconciliationService: reconciled ref={} paystackRef={} via verify " +
                    "(Paystack reports success)", reference, txn.getExternalReference());
        } else if ("failed".equalsIgnoreCase(status) || "abandoned".equalsIgnoreCase(status)) {
            txn.markFailed(Instant.now(clock));
            transactionRepo.save(txn);
            log.info("DepositReconciliationService: marked ref={} FAILED — Paystack reports status={}",
                    reference, status);
        } else {
            log.debug("DepositReconciliationService: ref={} still {} on Paystack's side — retrying next run",
                    reference, status);
        }
    }
}
