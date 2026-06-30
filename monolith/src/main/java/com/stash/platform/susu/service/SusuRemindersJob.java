package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.event.SusuContributionReminderEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Hourly job that emits contribution reminder events for members who have not
 * yet paid as their round due date approaches.
 *
 * Two reminder windows per outstanding contribution:
 * - 48H: due date is between 24h and 48h from now
 * - 24H: due date is between 0h and 24h from now (still COLLECTING)
 *
 * Idempotency is guaranteed by the {@code susu_contribution_reminders} table.
 * Re-running the job within the same hour emits zero duplicate events.
 *
 * Only PENDING contributions in COLLECTING rounds are selected. This job only
 * emits {@code susu.contribution.reminder} events; the v0.5 notification worker
 * handles delivery.
 */
@Component
public class SusuRemindersJob {

    private static final Logger log = LoggerFactory.getLogger(SusuRemindersJob.class);
    private static final int    BATCH_SIZE = 200;

    private final SusuContributionRepository        contributionRepo;
    private final SusuContributionReminderProcessor processor;
    private final Clock                             clock;

    public SusuRemindersJob(SusuContributionRepository contributionRepo,
                             SusuContributionReminderProcessor processor,
                             Clock clock) {
        this.contributionRepo = contributionRepo;
        this.processor        = processor;
        this.clock            = clock;
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void emitReminders() {
        String  correlationId = "reminders-" + UUID.randomUUID();
        Instant now           = Instant.now(clock);

        log.info("SusuRemindersJob: starting at={} correlation={}", now, correlationId);

        // ── 48H window: due date between now+24h and now+48h ─────────────
        Instant window48Start = now.plus(24, ChronoUnit.HOURS);
        Instant window48End   = now.plus(48, ChronoUnit.HOURS);

        List<SusuContributionEntity> due48h = contributionRepo.findPendingContributionsInWindow(
                window48Start, window48End, BATCH_SIZE);

        int emitted48 = 0, skipped48 = 0;
        for (SusuContributionEntity c : due48h) {
            try {
                processor.processOne(c, SusuContributionReminderEvent.TYPE_48H, correlationId);
                emitted48++;
            } catch (Exception e) {
                skipped48++;
                log.error("SusuRemindersJob: 48H reminder failed for contribution={}: {}",
                        c.getId(), e.getMessage());
            }
        }

        // ── 24H window: due date between now and now+24h ──────────────────
        Instant window24Start = now;
        Instant window24End   = now.plus(24, ChronoUnit.HOURS);

        List<SusuContributionEntity> due24h = contributionRepo.findPendingContributionsInWindow(
                window24Start, window24End, BATCH_SIZE);

        int emitted24 = 0, skipped24 = 0;
        for (SusuContributionEntity c : due24h) {
            try {
                processor.processOne(c, SusuContributionReminderEvent.TYPE_24H, correlationId);
                emitted24++;
            } catch (Exception e) {
                skipped24++;
                log.error("SusuRemindersJob: 24H reminder failed for contribution={}: {}",
                        c.getId(), e.getMessage());
            }
        }

        log.info("SusuRemindersJob: complete. 48H: emitted={} skipped={}. " +
                 "24H: emitted={} skipped={}. correlation={}",
                emitted48, skipped48, emitted24, skipped24, correlationId);
    }
}
