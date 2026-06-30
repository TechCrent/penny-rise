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

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class SusuOrganiserMemberRemovalService {

    private static final Logger log =
            LoggerFactory.getLogger(SusuOrganiserMemberRemovalService.class);

    private final SusuGroupRepository       groupRepo;
    private final SusuMembershipRepository  membershipRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock                     clock;

    public SusuOrganiserMemberRemovalService(
            SusuGroupRepository groupRepo,
            SusuMembershipRepository membershipRepo,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.groupRepo      = groupRepo;
        this.membershipRepo = membershipRepo;
        this.eventPublisher = eventPublisher;
        this.clock          = clock;
    }

    @Transactional
    public void removeMember(UUID groupId, UUID targetUserId,
                              UUID callerId, String correlationId) {

        SusuGroupEntity group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Susu group not found."));

        if (!callerId.equals(group.getOrganiserUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the group organiser can remove members.");
        }

        if ("ACTIVE".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_CANNOT_REMOVE_FROM_ACTIVE_GROUP: Removing members from an " +
                    "active susu requires the disputes process. Contact support.");
        }

        if (!"PENDING".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Members can only be removed from a group that is PENDING.");
        }

        if (callerId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "SUSU_ORGANISER_CANNOT_REMOVE_SELF: The organiser cannot remove " +
                    "themselves. To leave the group, use the leave endpoint — " +
                    "this will cancel the group.");
        }

        SusuMembershipEntity membership =
                membershipRepo.findByGroupAndUser(groupId, targetUserId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "This user is not a member of the group."));

        if (!"ACTIVE".equals(membership.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This member is already " + membership.getStatus().toLowerCase() + ".");
        }

        Instant now = Instant.now(clock);
        setField(membership, "status",    "REMOVED");
        setField(membership, "removedAt", now);
        membershipRepo.save(membership);

        log.info("SusuOrganiserMemberRemoval: group={} removed member={} by organiser={} " +
                 "correlation={}", groupId, targetUserId, callerId, correlationId);

        eventPublisher.publishEvent(new SusuMemberLeftEvent(
                this, groupId, targetUserId, "REMOVED",
                "PENDING", false, correlationId, now));
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot set field " + name, e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name)
            throws NoSuchFieldException {
        try { return c.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (c.getSuperclass() != null) return findField(c.getSuperclass(), name);
            throw e;
        }
    }
}
