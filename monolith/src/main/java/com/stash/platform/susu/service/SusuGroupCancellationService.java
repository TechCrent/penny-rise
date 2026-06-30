package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.event.SusuGroupCancelledEvent;
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
import java.util.List;
import java.util.UUID;

@Service
public class SusuGroupCancellationService {

    private static final Logger log = LoggerFactory.getLogger(SusuGroupCancellationService.class);

    private final SusuGroupRepository       groupRepo;
    private final SusuMembershipRepository  membershipRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock                     clock;

    public SusuGroupCancellationService(SusuGroupRepository groupRepo,
                                         SusuMembershipRepository membershipRepo,
                                         ApplicationEventPublisher eventPublisher,
                                         Clock clock) {
        this.groupRepo      = groupRepo;
        this.membershipRepo = membershipRepo;
        this.eventPublisher = eventPublisher;
        this.clock          = clock;
    }

    @Transactional
    public void cancelGroup(UUID groupId, UUID callerId, String correlationId) {

        SusuGroupEntity group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Susu group not found."));

        if (!callerId.equals(group.getOrganiserUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the group organiser can cancel this group.");
        }

        if ("ACTIVE".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_CANNOT_CANCEL_ACTIVE_GROUP: An active susu group cannot be " +
                    "cancelled here. Contact support to initiate the disputes process.");
        }

        if ("CANCELLED".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This group is already cancelled.");
        }

        if ("COMPLETED".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A completed group cannot be cancelled.");
        }

        Instant now = Instant.now(clock);

        List<UUID> affectedMemberIds = membershipRepo.findActiveUserIds(groupId);

        int transitioned = membershipRepo.cancelAllActiveMemberships(groupId, now);

        setField(group, "status", "CANCELLED");
        groupRepo.save(group);

        log.info("SusuGroupCancellation: group={} CANCELLED by organiser={} " +
                 "membershipsTransitioned={} correlation={}",
                groupId, callerId, transitioned, correlationId);

        eventPublisher.publishEvent(new SusuGroupCancelledEvent(
                this, groupId, group.getName(),
                group.getOrganiserUserId(), affectedMemberIds,
                correlationId, now));
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
