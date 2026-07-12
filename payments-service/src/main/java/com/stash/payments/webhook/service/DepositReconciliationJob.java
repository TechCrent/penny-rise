package com.stash.payments.webhook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled wrapper around {@link DepositReconciliationService} — runs every
 * 60s, verifying any deposit that's been PENDING longer than the configured
 * threshold directly against Paystack instead of waiting on a webhook that
 * may never arrive (the recurring local-dev pain point) or that a real
 * deployment's webhook delivery missed transiently.
 */
@Component
public class DepositReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(DepositReconciliationJob.class);

    private final DepositReconciliationService service;

    public DepositReconciliationJob(DepositReconciliationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "60000")
    public void reconcilePendingDeposits() {
        List<String> references = service.findStaleDepositReferences();
        if (references.isEmpty()) {
            return;
        }

        log.info("DepositReconciliationJob: checking {} stale PENDING deposit(s) against Paystack",
                references.size());

        for (String reference : references) {
            try {
                service.reconcileOne(reference);
            } catch (Exception e) {
                log.error("DepositReconciliationJob: unexpected error reconciling ref={}: {}",
                        reference, e.getMessage(), e);
                // Keep going — one bad reference shouldn't block the rest of the batch.
            }
        }
    }
}
