package com.stash.payments.webhook.service;

import com.stash.payments.moolre.client.MoolreClient;
import com.stash.payments.moolre.dto.StatusResult;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Self-heals deposits stuck PENDING because Moolre's payment webhook was
 * missed, delayed, or (in local dev, with no public callback URL) never had
 * anywhere to land. Calls Moolre's status API — the same outcome path
 * {@link ChargeSuccessHandler} would process a webhook through — so a deposit
 * is never marked COMPLETED on our say-so alone, only on Moolre's.
 *
 * <p>The {@code pendingThreshold} delay (default 60s) exists so this doesn't
 * race the real webhook on every ordinary deposit.
 */
@Service
public class DepositReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(DepositReconciliationService.class);

    private final TransactionRepository transactionRepo;
    private final MoolreClient moolreClient;
    private final ChargeSuccessHandler chargeSuccessHandler;
    private final Clock clock;
    private final Duration pendingThreshold;

    public DepositReconciliationService(
            TransactionRepository transactionRepo,
            MoolreClient moolreClient,
            ChargeSuccessHandler chargeSuccessHandler,
            Clock clock,
            @Value("${stash.moolre.reconciliation.pending-threshold-seconds:${stash.paystack.reconciliation.pending-threshold-seconds:60}}")
            long pendingThresholdSeconds) {
        this.transactionRepo = transactionRepo;
        this.moolreClient = moolreClient;
        this.chargeSuccessHandler = chargeSuccessHandler;
        this.clock = clock;
        this.pendingThreshold = Duration.ofSeconds(pendingThresholdSeconds);
    }

    public List<String> findStaleDepositReferences() {
        Instant threshold = Instant.now(clock).minus(pendingThreshold);
        return transactionRepo.findStalePendingDepositReferences(threshold);
    }

    /**
     * Verifies one deposit against Moolre and completes/fails it if
     * Moolre reports a terminal outcome. Queries by our STSH
     * {@code externalref} ({@code idtype=1}).
     */
    @Transactional
    public void reconcileOne(String reference) {
        TransactionEntity txn = transactionRepo.findByReference(reference).orElse(null);
        if (txn == null || !"PENDING".equals(txn.getStatus())) {
            return;
        }

        StatusResult status;
        try {
            // Lookup by our STSH reference (externalref we sent to Moolre)
            status = moolreClient.queryStatus(reference, true);
        } catch (Exception e) {
            log.warn("DepositReconciliationService: queryStatus failed for ref={}: {}",
                    reference, e.getMessage());
            return;
        }

        if (status.txStatus() != null && status.txStatus() == 1) {
            Map<String, Object> data = new HashMap<>();
            data.put("externalref", status.externalRef() != null ? status.externalRef() : reference);
            data.put("reference", reference);
            data.put("amount", status.amount());
            if (status.transactionId() != null) {
                data.put("transactionid", status.transactionId());
            }
            chargeSuccessHandler.handle(data, txn.getCorrelationId());
            log.info("DepositReconciliationService: reconciled ref={} via Moolre status " +
                    "(txstatus=1)", reference);
        } else if (status.txStatus() != null && status.txStatus() == 2) {
            txn.markFailed(Instant.now(clock));
            transactionRepo.save(txn);
            log.info("DepositReconciliationService: marked ref={} FAILED — Moolre reports txstatus=2",
                    reference);
        } else {
            log.debug("DepositReconciliationService: ref={} still txstatus={} on Moolre — retrying next run",
                    reference, status.txStatus());
        }
    }
}
