package com.stash.platform.susu.event;

import com.stash.platform.susu.repository.SusuGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * v0.5-034: flags a susu group for admin review when a member's late
 * penalty is waived (SusuContributionLateEvent.penaltyWaived = true) — a
 * real financial shortfall, confirmed against SusuLatePenaltyProcessor's
 * real waive paths (SUSU_POT unresolvable, member wallet unresolvable, the
 * penalty charge call failing, or Payments itself reporting insufficient
 * balance — all four converge on the same waived=true event).
 *
 * <p>Deliberately a plain {@code @EventListener}, not the
 * {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async}
 * pattern {@link SusuEventPublisher} uses for its RabbitMQ publish. That
 * pattern exists specifically because publishing is external I/O that
 * shouldn't block or be entangled with the DB transaction. Flagging is a
 * pure DB write with no external call — running it synchronously, inside
 * {@link com.stash.platform.susu.service.SusuLatePenaltyProcessor#process}'s
 * own {@code REQUIRES_NEW} transaction, means the contribution being
 * marked LATE and the group being flagged either both commit or both roll
 * back together, rather than risking the flag update being lost if
 * something failed between commit and an async listener running.
 */
@Component
public class SusuGroupFlaggingListener {

    private static final Logger log = LoggerFactory.getLogger(SusuGroupFlaggingListener.class);

    private final SusuGroupRepository groupRepo;
    private final Clock clock;

    public SusuGroupFlaggingListener(SusuGroupRepository groupRepo, Clock clock) {
        this.groupRepo = groupRepo;
        this.clock = clock;
    }

    @EventListener
    public void onContributionLate(SusuContributionLateEvent event) {
        if (!event.isPenaltyWaived()) {
            return;
        }

        Instant now = Instant.now(clock);
        groupRepo.flagForReview(event.getGroupId(), now);

        log.warn("SusuGroupFlagging: group={} flagged for review — penalty waived for " +
                 "contribution={} member={} correlation={}",
                event.getGroupId(), event.getContributionId(), event.getMemberUserId(),
                event.getCorrelationId());
    }
}
