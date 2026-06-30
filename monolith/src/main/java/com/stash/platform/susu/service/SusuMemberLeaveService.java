package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.event.SusuMemberLeftEvent;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
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
import java.util.UUID;

/**
 * Handles voluntary member departure from a susu group.
 *
 * <p><strong>User-facing rule:</strong> members may only leave {@code PENDING} groups.
 * Once a group is {@code ACTIVE}, leaving requires the admin disputes path (v0.5).
 *
 * <p><strong>Organiser leaving a PENDING group:</strong> the group is automatically
 * cancelled — a group without its organiser cannot proceed. All other members'
 * memberships remain; the group status becomes {@code CANCELLED}.
 *
 * <p><strong>Atomicity:</strong> membership status update and group cancellation
 * (if applicable) commit together. The {@code SusuMemberLeftEvent} fires AFTER_COMMIT.
 */
@Service
public class SusuMemberLeaveService {

    private static final Logger log = LoggerFactory.getLogger(SusuMemberLeaveService.class);

    private final SusuGroupRepository       groupRepo;
    private final SusuMembershipRepository  membershipRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock                     clock;

    public SusuMemberLeaveService(SusuGroupRepository groupRepo,
                                   SusuMembershipRepository membershipRepo,
                                   ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.groupRepo      = groupRepo;
        this.membershipRepo = membershipRepo;
        this.eventPublisher = eventPublisher;
        this.clock          = clock;
    }

    @Transactional
    public void leaveGroup(UUID groupId, UUID callerId, String correlationId) {
        SusuGroupEntity group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Susu group not found: " + groupId));

        if ("ACTIVE".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_CANNOT_LEAVE_ACTIVE_GROUP: You cannot leave an active susu group. " +
                    "Contact support if you need to be removed.");
        }

        if ("COMPLETED".equals(group.getStatus()) || "CANCELLED".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_GROUP_NOT_JOINABLE: This group is " + group.getStatus().toLowerCase() +
                    " and you cannot leave it.");
        }

        SusuMembershipEntity membership =
                membershipRepo.findByGroupAndUser(groupId, callerId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "You are not a member of this group."));

        if (!"ACTIVE".equals(membership.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You are not an active member of this group " +
                    "(current status: " + membership.getStatus() + ").");
        }

        Instant now = Instant.now(clock);

        setField(membership, "status",    "LEFT");
        setField(membership, "removedAt", now);
        membershipRepo.save(membership);

        boolean groupCancelled = callerId.equals(group.getOrganiserUserId());
        if (groupCancelled) {
            setField(group, "status", "CANCELLED");
            groupRepo.save(group);
            log.info("SusuGroupCancelled: group={} organiser={} left — group auto-cancelled. " +
                     "correlation={}", groupId, callerId, correlationId);
        }

        log.info("SusuMemberLeft: group={} member={} status=LEFT organiserLeft={} correlation={}",
                groupId, callerId, groupCancelled, correlationId);

        eventPublisher.publishEvent(new SusuMemberLeftEvent(
                this, groupId, callerId, "LEFT",
                groupCancelled ? "CANCELLED" : group.getStatus(),
                groupCancelled, correlationId, now));
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