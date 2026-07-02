package com.stash.platform.susu.service;

import com.stash.platform.susu.api.dto.SusuGroupDetailResponse;
import com.stash.platform.susu.api.dto.SusuGroupListItemResponse;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only query service for susu group list and detail.
 *
 * <p>Uses separate targeted queries rather than a single mega-join:
 * <ul>
 *   <li>Group rows loaded via {@link SusuGroupRepository}</li>
 *   <li>Membership rows loaded via {@link SusuMembershipRepository}</li>
 *   <li>Current round loaded via {@link SusuRoundRepository}</li>
 *   <li>Contributions for current round loaded via {@link SusuContributionRepository}</li>
 *   <li>User display names resolved in one batch via {@link UserRepository#findAllById}</li>
 * </ul>
 * This avoids N+1 queries while keeping each query simple and independently testable.
 */
@Service
@Transactional(readOnly = true)
public class SusuGroupQueryService {

    private static final Logger log = LoggerFactory.getLogger(SusuGroupQueryService.class);

    private static final Set<String> INACTIVE_STATUSES = Set.of("CANCELLED", "COMPLETED");

    private final SusuGroupRepository        groupRepo;
    private final SusuMembershipRepository   membershipRepo;
    private final SusuRoundRepository        roundRepo;
    private final SusuContributionRepository contributionRepo;
    private final UserRepository             userRepo;

    public SusuGroupQueryService(SusuGroupRepository groupRepo,
                                  SusuMembershipRepository membershipRepo,
                                  SusuRoundRepository roundRepo,
                                  SusuContributionRepository contributionRepo,
                                  UserRepository userRepo) {
        this.groupRepo        = groupRepo;
        this.membershipRepo   = membershipRepo;
        this.roundRepo        = roundRepo;
        this.contributionRepo = contributionRepo;
        this.userRepo         = userRepo;
    }

    // ── Name lookup (v0.5-020) ───────────────────────────────────────────

    /**
     * Returns the user-facing name of a susu group by its ID.
     * Used by TransactionHistoryEnricher to populate account_name for
     * SUSU_CONTRIBUTION / SUSU_DISBURSEMENT transaction rows.
     */
    public Optional<String> getGroupName(UUID groupId) {
        return groupRepo.findById(groupId).map(SusuGroupEntity::getName);
    }

    // ── List ──────────────────────────────────────────────────────────────

    /**
     * Returns all susu groups the caller is an active member of,
     * ordered by group creation date descending.
     *
     * @param callerId        authenticated user
     * @param includeInactive if true, includes CANCELLED and COMPLETED groups
     */
    public List<SusuGroupListItemResponse> listGroups(UUID callerId, boolean includeInactive,
                                                        String correlationId) {
        List<SusuMembershipEntity> memberships =
                membershipRepo.findActiveMembershipsByUser(callerId);

        if (memberships.isEmpty()) {
            return List.of();
        }

        Set<UUID> groupIds = memberships.stream()
                .map(SusuMembershipEntity::getSusuGroupId)
                .collect(Collectors.toSet());
        Map<UUID, SusuGroupEntity> groupsById = groupRepo.findAllById(groupIds).stream()
                .collect(Collectors.toMap(SusuGroupEntity::getId, Function.identity()));

        List<SusuGroupListItemResponse> result = new ArrayList<>();

        for (SusuMembershipEntity membership : memberships) {
            SusuGroupEntity group = groupsById.get(membership.getSusuGroupId());
            if (group == null) continue;

            if (!includeInactive && INACTIVE_STATUSES.contains(group.getStatus())) {
                continue;
            }

            long memberCount = membershipRepo.countActiveMembers(group.getId());

            List<SusuRoundEntity> rounds = roundRepo.findAllByGroup(group.getId());
            Integer totalRounds = rounds.isEmpty() ? null : rounds.size();

            SusuRoundEntity currentRound = null;
            if (group.getCurrentRoundNumber() != null) {
                currentRound = roundRepo.findByGroupAndRoundNumber(
                        group.getId(), group.getCurrentRoundNumber()).orElse(null);
            }

            result.add(SusuGroupListItemResponse.of(
                    group, membership, memberCount, totalRounds, currentRound, callerId));
        }

        // Sort by group created_at descending — memberships are ordered by
        // joinedAt, which may differ from the group's own created_at.
        result.sort(Comparator.comparing(SusuGroupListItemResponse::createdAt).reversed());

        log.debug("SusuGroupQuery: listed {} groups for user={} includeInactive={} correlation={}",
                result.size(), callerId, includeInactive, correlationId);

        return result;
    }

    // ── Detail ─────────────────────────────────────────────────────────────

    /**
     * Returns the full group state for an active member.
     *
     * @throws ResponseStatusException 404 if group not found;
     *                                 403 if caller is not an active member
     */
    public SusuGroupDetailResponse getGroupDetail(UUID groupId, UUID callerId,
                                                   String correlationId) {
        SusuGroupEntity group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Susu group not found: " + groupId));

        SusuMembershipEntity callerMembership =
                membershipRepo.findByGroupAndUser(groupId, callerId)
                        .filter(m -> "ACTIVE".equals(m.getStatus()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "You are not an active member of this group."));

        List<SusuMembershipEntity> members =
                membershipRepo.findActiveMembersByGroup(groupId);

        Set<UUID> userIds = new HashSet<>();
        members.forEach(m -> userIds.add(m.getUserId()));
        userIds.add(group.getOrganiserUserId());

        SusuRoundEntity              currentRound  = null;
        List<SusuContributionEntity> contributions = List.of();
        if (group.getCurrentRoundNumber() != null) {
            currentRound = roundRepo.findByGroupAndRoundNumber(
                    groupId, group.getCurrentRoundNumber()).orElse(null);
            if (currentRound != null) {
                contributions = contributionRepo.findByRound(currentRound.getId());
                contributions.forEach(c -> userIds.add(c.getMemberUserId()));
                if (currentRound.getRecipientUserId() != null) {
                    userIds.add(currentRound.getRecipientUserId());
                }
            }
        }

        Map<UUID, String> displayNames = userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        int totalRounds = roundRepo.findAllByGroup(groupId).size();

        log.debug("SusuGroupQuery: detail for group={} caller={} correlation={}",
                groupId, callerId, correlationId);

        return buildDetailResponse(group, callerMembership, members,
                currentRound, contributions, displayNames, totalRounds, callerId);
    }

    // ── Assembly ───────────────────────────────────────────────────────────

    private SusuGroupDetailResponse buildDetailResponse(
            SusuGroupEntity group,
            SusuMembershipEntity callerMembership,
            List<SusuMembershipEntity> members,
            SusuRoundEntity currentRound,
            List<SusuContributionEntity> contributions,
            Map<UUID, String> displayNames,
            int totalRounds,
            UUID callerId) {

        boolean isCallerOrganiser = callerId.equals(group.getOrganiserUserId());

        String cedis = SusuGroupDetailResponse.toCedis(group.getContributionAmount());

        SusuGroupDetailResponse.CurrentRoundSummary roundSummary = null;
        if (currentRound != null) {
            List<SusuGroupDetailResponse.ContributionStatus> contribStatuses =
                    contributions.stream()
                            .map(c -> new SusuGroupDetailResponse.ContributionStatus(
                                    c.getMemberUserId(),
                                    displayNames.getOrDefault(c.getMemberUserId(), "Unknown"),
                                    c.getStatus(),
                                    c.isLate(),
                                    c.getPenaltyAmount(),
                                    c.getPaidAt()
                            ))
                            .toList();

            roundSummary = new SusuGroupDetailResponse.CurrentRoundSummary(
                    currentRound.getId(),
                    currentRound.getRoundNumber(),
                    totalRounds,
                    currentRound.getStatus(),
                    currentRound.getRecipientUserId(),
                    displayNames.getOrDefault(currentRound.getRecipientUserId(), "Unknown"),
                    currentRound.getScheduledCollectionAt(),
                    currentRound.getExpectedPotAmount(),
                    SusuGroupDetailResponse.toCedis(currentRound.getExpectedPotAmount()),
                    currentRound.getActualPotAmount(),
                    contribStatuses
            );
        }

        List<SusuGroupDetailResponse.MemberSummary> memberSummaries = members.stream()
                .map(m -> new SusuGroupDetailResponse.MemberSummary(
                        m.getUserId(),
                        displayNames.getOrDefault(m.getUserId(), "Unknown"),
                        m.getRotationPosition(),
                        m.getStatus(),
                        m.getJoinedAt(),
                        m.getUserId().equals(group.getOrganiserUserId())
                ))
                .sorted(Comparator.comparing(
                        s -> s.rotationPosition() != null ? s.rotationPosition() : Integer.MAX_VALUE
                ))
                .toList();

        SusuGroupDetailResponse.CallerMembership callerSummary =
                new SusuGroupDetailResponse.CallerMembership(
                        callerMembership.getId(),
                        callerMembership.getRotationPosition(),
                        callerMembership.getStatus(),
                        callerMembership.getJoinedAt()
                );

        String startDateStr = group.getStartDate() != null
                ? group.getStartDate().toString() : null;

        return new SusuGroupDetailResponse(
                group.getId(), group.getName(), group.getStatus(),
                group.getOrganiserUserId(), isCallerOrganiser,
                group.getContributionAmount(), cedis,
                group.getFrequency(), group.getTargetMemberCount(),
                group.getJoinCode(), startDateStr,
                group.getCreatedAt(),
                roundSummary, memberSummaries, callerSummary
        );
    }
}
