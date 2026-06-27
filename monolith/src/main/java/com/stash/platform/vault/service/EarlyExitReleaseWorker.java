package com.stash.platform.vault.service;

import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.domain.EarlyExitRequestEntity;
import com.stash.platform.vault.repository.EarlyExitRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled worker that processes due locked-vault early-exit requests.
 *
 * <p>Runs every 15 minutes. Selects PENDING requests where
 * {@code scheduled_release_at <= now()} using {@code SELECT FOR UPDATE SKIP LOCKED}
 * so multiple worker instances can run safely in parallel without double-processing.
 *
 * <p>CANCELLED requests are never returned by the query (filtered on status=PENDING)
 * and are therefore skipped automatically.
 *
 * <p><strong>MoMo details:</strong> captured on the early-exit request row at
 * request-creation time (v0.3-031, extended to capture MoMo details after an
 * audit found the worker falling back to the user's registered phone number,
 * which can change or be cleared during the 72-hour cool-off). The user
 * profile still has no dedicated MoMo field (planned for v0.5), but the
 * worker no longer depends on it.
 */
@Component
public class EarlyExitReleaseWorker {

    private static final Logger log = LoggerFactory.getLogger(EarlyExitReleaseWorker.class);
    private static final int    BATCH_SIZE = 20;

    private final EarlyExitRequestRepository requestRepo;
    private final UserRepository             userRepo;
    private final EarlyExitReleaseProcessor  processor;
    private final EarlyExitReleaseMetrics    metrics;
    private final Clock                      clock;

    public EarlyExitReleaseWorker(EarlyExitRequestRepository requestRepo,
                                   UserRepository userRepo,
                                   EarlyExitReleaseProcessor processor,
                                   EarlyExitReleaseMetrics metrics,
                                   Clock clock) {
        this.requestRepo = requestRepo;
        this.userRepo    = userRepo;
        this.processor   = processor;
        this.metrics     = metrics;
        this.clock       = clock;
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)   // every 15 minutes
    @Transactional
    public void processReleases() {
        Instant now = Instant.now(clock);
        List<EarlyExitRequestEntity> dueRequests =
                requestRepo.findDueRequests(now, BATCH_SIZE);

        metrics.updatePendingCount(dueRequests.size());

        if (dueRequests.isEmpty()) {
            log.debug("EarlyExitReleaseWorker: no due requests at {}", now);
            return;
        }

        log.info("EarlyExitReleaseWorker: processing {} due early-exit request(s) at {}",
                dueRequests.size(), now);

        for (EarlyExitRequestEntity request : dueRequests) {
            processOne(request, now);
        }
    }

    private void processOne(EarlyExitRequestEntity request, Instant now) {
        String correlationId = "early-exit-release-" + request.getId();

        // Skip already-exhausted requests (should not appear in query but defensive)
        if (request.getAttempts() >= EarlyExitReleaseProcessor.MAX_ATTEMPTS) {
            log.warn("EarlyExitReleaseWorker: skipping request={} — already at max attempts. " +
                     "Requires manual intervention.", request.getId());
            return;
        }

        // Load user details (full name, email — for the Paystack recipient).
        // MoMo details come from the request row itself, captured at request time.
        Optional<User> userOpt = userRepo.findById(request.getRequestedByUserId());
        if (userOpt.isEmpty()) {
            log.error("[P0_ALERT] EarlyExitReleaseWorker: user not found for request={}. " +
                      "MANUAL INTERVENTION REQUIRED.", request.getId());
            metrics.recordP0Alert();
            return;
        }

        User user = userOpt.get();
        String momoNumber   = request.getDestinationMomoNumber();
        String momoProvider = request.getMomoProvider();

        if (momoNumber == null || momoNumber.isBlank()) {
            // Should not happen for requests created after V10 (the column is
            // NOT NULL and validated at request time) — defensive for any
            // pre-migration row that slipped through with the backfilled ''.
            log.error("[P0_ALERT] EarlyExitReleaseWorker: no MoMo number on request={} " +
                      "(pre-migration row?). MANUAL INTERVENTION REQUIRED.",
                    request.getId());
            metrics.recordP0Alert();
            return;
        }

        try {
            processor.process(
                    request,
                    momoNumber,
                    momoProvider,
                    user.getDisplayName(),
                    user.getEmail(),
                    correlationId
            );
        } catch (Exception e) {
            // processor handles failure tracking — this catch is belt-and-suspenders
            log.error("EarlyExitReleaseWorker: unexpected exception for request={}: {}",
                    request.getId(), e.getMessage(), e);
        }
    }
}
