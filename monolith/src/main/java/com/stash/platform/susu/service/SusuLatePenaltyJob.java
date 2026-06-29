package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.repository.SusuContributionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Daily job that identifies overdue susu contributions and applies the late penalty.
 *
 * <p>Runs at 08:00 UTC. Selects {@code PENDING} contributions where the round's
 * {@code scheduled_collection_at + grace_period_hours <= now()}. Only PENDING
 * contributions are selected — LATE and PAID rows are excluded, making re-runs
 * naturally idempotent.
 *
 * <p>The job does NOT block or delay disbursement. The disbursement worker
 * queries for {@code DISBURSING} rounds and fires independently. A round can
 * disburse even if some contributions are LATE — the pot amount reflects only
 * PAID contributions ({@code actual_pot_amount}, set in v0.4-009).
 *
 * <p><strong>System constant:</strong> total penalty is GHS 5 (500 pesewas) per
 * Decision 13. Split 50/50: 250p to SUSU_POT, 250p to PENALTY_REVENUE. Stored
 * in {@code stash.susu.late-penalty.total-amount-pesewas}; not hardcoded here.
 */
@Component
public class SusuLatePenaltyJob {

    private static final Logger log = LoggerFactory.getLogger(SusuLatePenaltyJob.class);
    private static final int    BATCH_SIZE = 100;

    private final SusuContributionRepository contributionRepo;
    private final SusuLatePenaltyProcessor   processor;
    private final Clock                      clock;
    private final long                       gracePeriodHours;

    public SusuLatePenaltyJob(
            SusuContributionRepository contributionRepo,
            SusuLatePenaltyProcessor processor,
            Clock clock,
            @Value("${stash.susu.late-penalty.grace-period-hours:24}")
                    long gracePeriodHours) {
        this.contributionRepo = contributionRepo;
        this.processor        = processor;
        this.clock            = clock;
        this.gracePeriodHours = gracePeriodHours;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
    public void applyLatePenalties() {
        Instant now    = Instant.now(clock);
        Instant cutoff = now.minus(Duration.ofHours(gracePeriodHours));

        log.info("SusuLatePenaltyJob: starting. cutoff={} (grace={}h)", cutoff, gracePeriodHours);

        List<SusuContributionEntity> overdue =
                contributionRepo.findOverduePendingContributions(cutoff, BATCH_SIZE);

        if (overdue.isEmpty()) {
            log.debug("SusuLatePenaltyJob: no overdue contributions.");
            return;
        }

        log.info("SusuLatePenaltyJob: processing {} overdue contribution(s)", overdue.size());

        int processed = 0, failed = 0;

        for (SusuContributionEntity contribution : overdue) {
            String correlationId = "late-penalty-" + contribution.getId();
            try {
                processor.process(contribution, correlationId);
                processed++;
            } catch (Exception e) {
                failed++;
                log.error("SusuLatePenaltyJob: failed to process contribution={} error={} " +
                          "correlation={}", contribution.getId(), e.getMessage(), correlationId);
            }
        }

        log.info("SusuLatePenaltyJob: complete. processed={} failed={}", processed, failed);
    }
}
