package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.SusuActivationResponse;
import com.stash.platform.susu.client.SusuPaymentsException;
import com.stash.platform.susu.client.SusuPotProvisionClient;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.event.SusuActivatedEvent;
import com.stash.platform.susu.event.SusuRoundStartedEvent;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Activates a susu group in a single atomic transaction.
 *
 * <p><strong>Transaction boundary:</strong> everything from validation
 * through the final contribution INSERT is in one {@code @Transactional}.
 * If the Payments Service call ({@link SusuPotProvisionClient#provisionSusuPot})
 * fails, {@link SusuPaymentsException} propagates out of the transaction and
 * causes a full rollback — no group or round rows are committed.
 *
 * <p><strong>Schedule calculation:</strong>
 * <ul>
 *   <li>MONTHLY — round N collects on {@code start_date + N months}.</li>
 *   <li>WEEKLY  — round N collects on {@code start_date + N weeks}.</li>
 *   <li>BIWEEKLY — round N collects on {@code start_date + N × 2 weeks}.</li>
 * </ul>
 *
 * <p><strong>KYC check:</strong> uses {@code APPROVED} per Decision 6.
 *
 * <p><strong>Rotation order:</strong> organiser is position 1. All other
 * members are ordered by {@code joined_at ASC, id ASC}.
 */
@Service
public class SusuGroupActivationService {

    private static final Logger log = LoggerFactory.getLogger(SusuGroupActivationService.class);

    private final SusuGroupRepository        groupRepo;
    private final SusuMembershipRepository   membershipRepo;
    private final SusuRoundRepository        roundRepo;
    private final SusuContributionRepository contributionRepo;
    private final UserRepository             userRepo;
    private final SusuPotProvisionClient     potProvisionClient;
    private final ApplicationEventPublisher  eventPublisher;
    private final Clock                      clock;

    public SusuGroupActivationService(
            SusuGroupRepository groupRepo,
            SusuMembershipRepository membershipRepo,
            SusuRoundRepository roundRepo,
            SusuContributionRepository contributionRepo,
            UserRepository userRepo,
            SusuPotProvisionClient potProvisionClient,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.groupRepo          = groupRepo;
        this.membershipRepo     = membershipRepo;
        this.roundRepo          = roundRepo;
        this.contributionRepo   = contributionRepo;
        this.userRepo           = userRepo;
        this.potProvisionClient = potProvisionClient;
        this.eventPublisher     = eventPublisher;
        this.clock              = clock;
    }

    @Transactional
    public SusuActivationResponse activate(UUID groupId, UUID callerId,
                                            String correlationId) {
        // ── Load and validate group ───────────────────────────────────────
        SusuGroupEntity group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Susu group not found: " + groupId));

        if (!"PENDING".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_ALREADY_ACTIVATED: This group is already " +
                    group.getStatus().toLowerCase() + ".");
        }

        if (!callerId.equals(group.getOrganiserUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the group organiser may activate this group.");
        }

        // ── Load active members ordered for rotation ──────────────────────
        List<SusuMembershipEntity> members =
                membershipRepo.findActiveMembersByGroupForActivation(groupId);

        // ── Member count check ────────────────────────────────────────────
        if (members.size() != group.getTargetMemberCount()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "SUSU_MEMBER_COUNT_NOT_MET: Group has " + members.size() +
                    " members but requires " + group.getTargetMemberCount() + ".");
        }

        // ── KYC check — all members must be APPROVED ──────────────────────
        Set<UUID> memberUserIds = members.stream()
                .map(SusuMembershipEntity::getUserId)
                .collect(Collectors.toSet());

        Map<UUID, User> usersById = userRepo.findAllById(memberUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<UUID> nonApproved = members.stream()
                .map(SusuMembershipEntity::getUserId)
                .filter(uid -> {
                    User u = usersById.get(uid);
                    return u == null || !u.isKycApproved();
                })
                .toList();

        if (!nonApproved.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "SUSU_MEMBER_KYC_INCOMPLETE: The following members have not completed " +
                    "identity verification: " + nonApproved);
        }

        // ── Provision SUSU_POT ledger account (before DB writes — if this ─
        // ── fails, we want to roll back cleanly with no half-written rows) ─
        UUID ledgerAccountId;
        try {
            ledgerAccountId = potProvisionClient.provisionSusuPot(
                    groupId, group.getName(), correlationId);
        } catch (SusuPaymentsException e) {
            log.error("SUSU_POT provision failed for group={} correlation={}: {}",
                    groupId, correlationId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment service unavailable — could not set up the group's savings " +
                    "pot. Please try again.");
        }

        Instant   now       = Instant.now(clock);
        LocalDate startDate = LocalDate.now(clock);

        // ── Activate group row ────────────────────────────────────────────
        setGroupActive(group, startDate, ledgerAccountId);
        groupRepo.save(group);

        // ── Assign rotation positions in order ────────────────────────────
        // Organiser is position 1. Remaining members ordered by joined_at ASC,
        // then id ASC as tiebreaker.
        List<SusuMembershipEntity> ordered = new ArrayList<>();
        members.stream()
                .filter(m -> m.getUserId().equals(group.getOrganiserUserId()))
                .findFirst()
                .ifPresent(ordered::add);
        members.stream()
                .filter(m -> !m.getUserId().equals(group.getOrganiserUserId()))
                .sorted(Comparator.comparing(SusuMembershipEntity::getJoinedAt)
                        .thenComparing(SusuMembershipEntity::getId))
                .forEach(ordered::add);

        for (int pos = 1; pos <= ordered.size(); pos++) {
            setMemberPosition(ordered.get(pos - 1), pos);
            membershipRepo.save(ordered.get(pos - 1));
        }

        int N = ordered.size();

        // ── Generate N rounds ─────────────────────────────────────────────
        long expectedPotAmount = (long) group.getContributionAmount() * N;
        List<SusuRoundEntity> rounds = new ArrayList<>();

        for (int roundNum = 1; roundNum <= N; roundNum++) {
            SusuMembershipEntity recipient = ordered.get(roundNum - 1);
            Instant collectionAt = computeCollectionDate(
                    startDate, group.getFrequency(), roundNum);
            String roundStatus = (roundNum == 1) ? "COLLECTING" : "PENDING";

            SusuRoundEntity round = buildRound(
                    groupId, roundNum, recipient.getUserId(),
                    roundStatus, collectionAt, expectedPotAmount, now);
            rounds.add(roundRepo.save(round));
        }

        // ── Generate N contribution rows for round 1 ──────────────────────
        SusuRoundEntity round1 = rounds.get(0);
        List<SusuContributionEntity> round1Contributions = new ArrayList<>();

        for (SusuMembershipEntity member : ordered) {
            SusuContributionEntity contribution =
                    buildContribution(round1.getId(), groupId,
                            member.getUserId(), group.getContributionAmount(), now);
            round1Contributions.add(contributionRepo.save(contribution));
        }

        log.info("SusuGroup activated: id={} organiser={} members={} " +
                 "rounds={} ledgerAccount={} correlation={}",
                groupId, callerId, N, N, ledgerAccountId, correlationId);

        // ── Publish events AFTER_COMMIT ───────────────────────────────────
        eventPublisher.publishEvent(new SusuActivatedEvent(
                this, groupId, group.getName(), group.getOrganiserUserId(),
                ordered.stream().map(SusuMembershipEntity::getUserId).toList(),
                ledgerAccountId, now, correlationId));

        eventPublisher.publishEvent(new SusuRoundStartedEvent(
                this, groupId, round1.getId(), 1, N,
                round1.getRecipientUserId(),
                round1.getScheduledCollectionAt(), correlationId));

        // ── Build response ────────────────────────────────────────────────
        List<SusuActivationResponse.MemberInfo> memberInfos = ordered.stream()
                .map(m -> new SusuActivationResponse.MemberInfo(
                        m.getUserId(), m.getRotationPosition(), m.getJoinedAt()))
                .toList();

        SusuActivationResponse.RoundInfo round1Info = new SusuActivationResponse.RoundInfo(
                round1.getId(), 1, "COLLECTING",
                round1.getRecipientUserId(),
                round1.getScheduledCollectionAt(),
                expectedPotAmount,
                SusuActivationResponse.toCedis(expectedPotAmount),
                round1Contributions.size()
        );

        return new SusuActivationResponse(
                group.getId(), group.getName(), "ACTIVE",
                startDate, 1, ledgerAccountId,
                group.getContributionAmount(),
                SusuActivationResponse.toCedis(group.getContributionAmount()),
                group.getFrequency(), group.getTargetMemberCount(),
                memberInfos, round1Info, now
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Instant computeCollectionDate(LocalDate startDate, String frequency,
                                           int roundNumber) {
        LocalDate date = switch (frequency.toUpperCase()) {
            case "MONTHLY"  -> startDate.plusMonths(roundNumber);
            case "WEEKLY"   -> startDate.plusWeeks(roundNumber);
            case "BIWEEKLY" -> startDate.plusWeeks((long) roundNumber * 2);
            default -> throw new IllegalArgumentException(
                    "Unknown frequency: " + frequency);
        };
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private SusuRoundEntity buildRound(UUID groupId, int roundNum, UUID recipientUserId,
                                        String status, Instant collectionAt,
                                        long expectedPotAmount, Instant now) {
        SusuRoundEntity r = new SusuRoundEntity();
        setField(r, "susuGroupId",          groupId);
        setField(r, "roundNumber",           roundNum);
        setField(r, "recipientUserId",       recipientUserId);
        setField(r, "status",                status);
        setField(r, "scheduledCollectionAt", collectionAt);
        setField(r, "expectedPotAmount",     expectedPotAmount);
        return r;
    }

    private SusuContributionEntity buildContribution(UUID roundId, UUID groupId,
                                                      UUID memberUserId,
                                                      long expectedAmount, Instant now) {
        SusuContributionEntity c = new SusuContributionEntity();
        setField(c, "susuRoundId",            roundId);
        setField(c, "susuGroupId",            groupId);
        setField(c, "memberUserId",           memberUserId);
        setField(c, "expectedAmount",         expectedAmount);
        setField(c, "status",                 "PENDING");
        setField(c, "collectionAttemptCount", 0);
        setField(c, "penaltyAmount",          0L);
        setField(c, "isLate",                 false);
        setField(c, "createdAt",              now);
        return c;
    }

    private void setGroupActive(SusuGroupEntity group, LocalDate startDate,
                                 UUID ledgerAccountId) {
        setField(group, "status",             "ACTIVE");
        setField(group, "startDate",          startDate);
        setField(group, "currentRoundNumber", 1);
        setField(group, "ledgerAccountId",    ledgerAccountId);
    }

    private void setMemberPosition(SusuMembershipEntity member, int position) {
        setField(member, "rotationPosition", position);
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot set field " + name + " on " +
                    obj.getClass().getSimpleName(), e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String name)
            throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), name);
            }
            throw e;
        }
    }
}
