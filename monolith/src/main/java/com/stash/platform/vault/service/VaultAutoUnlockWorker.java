package com.stash.platform.vault.service;

import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled worker that checks locked vaults for naturally-met unlock conditions.
 *
 * <p>Runs every 15 minutes. {@code SELECT FOR UPDATE SKIP LOCKED} ensures
 * multiple worker instances can run safely in parallel.
 *
 * <p>Vaults already unlocked ({@code unlocked_at IS NOT NULL}) are excluded
 * from the candidate query — this makes the worker naturally idempotent.
 */
@Component
public class VaultAutoUnlockWorker {

    private static final Logger log = LoggerFactory.getLogger(VaultAutoUnlockWorker.class);
    private static final int    BATCH_SIZE = 50;

    private final VaultRepository         vaultRepo;
    private final VaultAutoUnlockProcessor processor;
    private final Clock                   clock;

    public VaultAutoUnlockWorker(VaultRepository vaultRepo,
                                  VaultAutoUnlockProcessor processor,
                                  Clock clock) {
        this.vaultRepo = vaultRepo;
        this.processor = processor;
        this.clock     = clock;
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)    // every 15 minutes
    @Transactional
    public void checkAndUnlock() {
        Instant now = Instant.now(clock);
        List<VaultEntity> candidates = vaultRepo.findUnlockCandidates(now, BATCH_SIZE);

        if (candidates.isEmpty()) {
            log.debug("VaultAutoUnlockWorker: no unlock candidates at {}", now);
            return;
        }

        log.info("VaultAutoUnlockWorker: evaluating {} candidate(s) at {}", candidates.size(), now);

        int unlocked = 0;
        for (VaultEntity vault : candidates) {
            String correlationId = "auto-unlock-" + vault.getId();
            try {
                if (processor.evaluate(vault, correlationId)) {
                    unlocked++;
                }
            } catch (Exception e) {
                // Log and continue — one failing vault must not block the rest
                log.error("VaultAutoUnlockWorker: unexpected error for vault={}: {}",
                        vault.getId(), e.getMessage(), e);
            }
        }

        log.info("VaultAutoUnlockWorker: run complete — {} unlocked of {} candidates",
                unlocked, candidates.size());
    }
}
