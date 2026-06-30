package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuContributionReminderEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuContributionReminderEvent;
import com.stash.platform.susu.repository.SusuContributionReminderRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Processes one contribution reminder attempt.
 *
 * Idempotency: before emitting the event, inserts a row into
 * {@code susu_contribution_reminders} with the UNIQUE constraint on
 * {@code (contribution_id, reminder_type)}. If the row already exists, the
 * INSERT fails with a {@link DataIntegrityViolationException}, which is caught
 * and the event is not published again.
 *
 * {@link Propagation#REQUIRES_NEW} so a failure on one contribution does not
 * roll back reminder records for others processed in the same batch.
 */
@Service
public class SusuContributionReminderProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(SusuContributionReminderProcessor.class);

    private final SusuContributionReminderRepository reminderRepo;
    private final SusuRoundRepository                roundRepo;
    private final ApplicationEventPublisher          eventPublisher;
    private final Clock                              clock;

    public SusuContributionReminderProcessor(
            SusuContributionReminderRepository reminderRepo,
            SusuRoundRepository roundRepo,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.reminderRepo   = reminderRepo;
        this.roundRepo      = roundRepo;
        this.eventPublisher = eventPublisher;
        this.clock          = clock;
    }

    /**
     * Attempts to emit a reminder for one contribution at one reminder type.
     * Idempotent: silently skips if the reminder was already sent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(SusuContributionEntity contribution,
                            String reminderType,
                            String correlationId) {

        UUID contributionId = contribution.getId();

        try {
            reminderRepo.save(SusuContributionReminderEntity.of(
                    contributionId, reminderType, Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            log.debug("ReminderProcessor: {} reminder already sent for contribution={}",
                    reminderType, contributionId);
            return;
        }

        SusuRoundEntity round = roundRepo.findById(contribution.getSusuRoundId())
                .orElse(null);
        if (round == null) {
            log.warn("ReminderProcessor: round not found for contribution={}. Skipping.",
                    contributionId);
            return;
        }

        Instant dueDate = round.getScheduledCollectionAt();

        eventPublisher.publishEvent(new SusuContributionReminderEvent(
                this,
                contribution.getSusuGroupId(),
                contribution.getSusuRoundId(),
                contributionId,
                contribution.getMemberUserId(),
                reminderType,
                contribution.getExpectedAmount(),
                dueDate,
                correlationId,
                Instant.now(clock)
        ));

        log.info("ReminderProcessor: {} reminder emitted for contribution={} " +
                 "member={} dueDate={} correlation={}",
                reminderType, contributionId,
                contribution.getMemberUserId(), dueDate, correlationId);
    }
}
