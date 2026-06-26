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
 * <p><strong>MoMo details:</strong> the user profile does not yet carry dedicated
 * MoMo fields (planned for v0.5). Until then, the registered {@code phone} is used
 * as the MoMo number and the provider defaults to {@link #FALLBACK_PROVIDER}.
 */
@Component
public class EarlyExitReleaseWorker {

    private static final Logger log = LoggerFactory.getLogger(EarlyExitReleaseWorker.class);
    private static final int    BATCH_SIZE = 20;

    // Fallback MoMo details when not found on the user profile.
    // In production, users must have registered MoMo details before early exit.
    // v0.5 will store MoMo number/provider on the user profile.
    private static final String FALLBACK_PROVIDER = "mtn";

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

        // Load user details (MoMo number = registered phone, full name, email)
        Optional<User> userOpt = userRepo.findById(request.getRequestedByUserId());
        if (userOpt.isEmpty()) {
            log.error("[P0_ALERT] EarlyExitReleaseWorker: user not found for request={}. " +
                      "MANUAL INTERVENTION REQUIRED.", request.getId());
            metrics.recordP0Alert();
            return;
        }

        User user = userOpt.get();
        String momoNumber   = user.getPhone();         // v0.5 will add a dedicated MoMo field
        String momoProvider = FALLBACK_PROVIDER;

        if (momoNumber == null || momoNumber.isBlank()) {
            log.error("[P0_ALERT] EarlyExitReleaseWorker: no MoMo number on user={} " +
                      "for request={}. MANUAL INTERVENTION REQUIRED.",
                    request.getRequestedByUserId(), request.getId());
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
