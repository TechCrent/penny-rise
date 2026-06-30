package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuMemberLeftEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin/dispute-path removal of a member from an ACTIVE susu group.
 *
 * <p>This service is intentionally separate from {@link SusuMemberLeaveService}
 * because it has different access control (internal endpoint, not user-facing)
 * and different effects (REMOVED status, future rounds SKIPPED).
 *
 * <p><strong>On removal from ACTIVE group:</strong>
 * <ol>
 *   <li>Membership transitions to {@code REMOVED}.</li>
 *   <li>All future {@code PENDING} contributions for this member are cancelled
 *       (set to {@code MISSED}) — they no longer owe contributions.</li>
 *   <li>All future {@code PENDING} rounds assigned to this member are marked
 *       {@code SKIPPED} — they will not receive the pot.</li>
 * </ol>
 *
 * <p>The pot roll-forward (adjusting next round's expected amount) is handled
 * by {@link SusuDisbursementProcessor} when it encounters a SKIPPED round.
 */
@Service
public class SusuMemberRemovalService {

    private static final Logger log = LoggerFactory.getLogger(SusuMemberRemovalService.class);

    private final SusuGroupRepository        groupRepo;
    private final SusuMembershipRepository   membershipRepo;
    private final SusuContributionRepository contributionRepo;
    private final SusuRoundRepository        roundRepo;
    private final ApplicationEventPublisher  eventPublisher;
    private final Clock                      clock;

    public SusuMemberRemovalService(
            SusuGroupRepository groupRepo,
            SusuMembershipRepository membershipRepo,
            SusuContributionRepository contributionRepo,
            SusuRoundRepository roundRepo,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.groupRepo        = groupRepo;
        this.membershipRepo   = membershipRepo;
        this.contributionRepo = contributionRepo;
        this.roundRepo        = roundRepo;
        this.eventPublisher   = eventPublisher;
        this.clock            = clock;
    }

    @Transactional
    public void removeMember(UUID groupId, UUID targetUserId,
                              UUID adminUserId, String correlationId) {
        SusuGroupEntity group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Group not found: " + groupId));

        if (!"ACTIVE".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Member removal from non-ACTIVE groups is handled via the leave endpoint.");
        }

        SusuMembershipEntity membership =
                membershipRepo.findByGroupAndUser(groupId, targetUserId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Member not found in this group."));

        if (!"ACTIVE".equals(membership.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Member is already " + membership.getStatus() + ".");
        }

        Instant now = Instant.now(clock);

        setField(membership, "status",    "REMOVED");
        setField(membership, "removedAt", now);
        membershipRepo.save(membership);

        int cancelled = contributionRepo.cancelPendingContributions(groupId, targetUserId);
        log.info("SusuMemberRemoval: group={} member={} cancelled {} pending contributions. " +
                 "admin={} correlation={}",
                groupId, targetUserId, cancelled, adminUserId, correlationId);

        List<SusuRoundEntity> pendingRounds =
                roundRepo.findPendingRoundsByRecipient(groupId, targetUserId);

        for (SusuRoundEntity round : pendingRounds) {
            setField(round, "status", "SKIPPED");
            roundRepo.save(round);
            log.info("SusuMemberRemoval: round={} number={} marked SKIPPED for removed member={}. " +
                     "correlation={}",
                    round.getId(), round.getRoundNumber(), targetUserId, correlationId);
        }

        // Also skip the current COLLECTING round if this member is the recipient — otherwise
        // disbursement would attempt to pay a removed member.
        Integer currentRoundNum = group.getCurrentRoundNumber();
        if (currentRoundNum != null) {
            roundRepo.findByGroupAndRoundNumber(groupId, currentRoundNum).ifPresent(current -> {
                if ("COLLECTING".equals(current.getStatus()) &&
                        targetUserId.equals(current.getRecipientUserId())) {
                    setField(current, "status", "SKIPPED");
                    roundRepo.save(current);
                    log.warn("SusuMemberRemoval: COLLECTING round={} number={} marked SKIPPED — " +
                             "removed member={} was the recipient. Pot rollforward at disbursement. " +
                             "correlation={}",
                            current.getId(), current.getRoundNumber(), targetUserId, correlationId);
                }
            });
        }

        log.info("SusuMemberRemoval: group={} member={} REMOVED. skippedRounds={} admin={}. " +
                 "correlation={}",
                groupId, targetUserId, pendingRounds.size(), adminUserId, correlationId);

        eventPublisher.publishEvent(new SusuMemberLeftEvent(
                this, groupId, targetUserId, "REMOVED",
                "ACTIVE", false, correlationId, now));
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            Field f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot set field " + name, e);
        }
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        try { return c.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (c.getSuperclass() != null) return findField(c.getSuperclass(), name);
            throw e;
        }
    }
}
