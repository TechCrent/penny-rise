package com.stash.admin.service;

import com.stash.admin.api.dto.AdminSusuGroupDetailResponse;
import com.stash.admin.api.dto.FlaggedSusuGroupListItem;
import com.stash.admin.api.dto.FlaggedSusuGroupListResponse;
import com.stash.admin.api.dto.SusuGroupContributionDetail;
import com.stash.admin.api.dto.SusuGroupMemberDetail;
import com.stash.admin.domain.AdminAuditActionEntity;
import com.stash.admin.integration.IntegrationPaymentsClient;
import com.stash.admin.repository.AdminAuditActionRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminSusuGroupService {

    private static final String ACTION_TYPE_CLEAR_FLAG = "SUSU_GROUP_FLAG_CLEARED";
    private static final String TARGET_TYPE_SUSU_GROUP = "SUSU_GROUP";

    private final SusuGroupRepository groupRepo;
    private final SusuMembershipRepository membershipRepo;
    private final SusuRoundRepository roundRepo;
    private final SusuContributionRepository contributionRepo;
    private final UserRepository userRepo;
    private final IntegrationPaymentsClient paymentsClient;
    private final AdminAuditActionRepository auditRepo;
    private final Clock clock;

    public AdminSusuGroupService(SusuGroupRepository groupRepo,
                                  SusuMembershipRepository membershipRepo,
                                  SusuRoundRepository roundRepo,
                                  SusuContributionRepository contributionRepo,
                                  UserRepository userRepo,
                                  IntegrationPaymentsClient paymentsClient,
                                  AdminAuditActionRepository auditRepo,
                                  Clock clock) {
        this.groupRepo = groupRepo;
        this.membershipRepo = membershipRepo;
        this.roundRepo = roundRepo;
        this.contributionRepo = contributionRepo;
        this.userRepo = userRepo;
        this.paymentsClient = paymentsClient;
        this.auditRepo = auditRepo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FlaggedSusuGroupListResponse listFlagged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SusuGroupEntity> result = groupRepo.findByFlaggedForReview(true, pageable);

        List<FlaggedSusuGroupListItem> items = result.getContent().stream()
                .map(this::toListItem)
                .toList();

        return new FlaggedSusuGroupListResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminSusuGroupDetailResponse getDetail(UUID groupId) {
        SusuGroupEntity group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "SUSU_GROUP_NOT_FOUND: No susu group with id " + groupId));

        List<SusuMembershipEntity> members = membershipRepo.findActiveMembersByGroup(groupId);

        Set<UUID> userIds = new HashSet<>();
        members.forEach(m -> userIds.add(m.getUserId()));
        userIds.add(group.getOrganiserUserId());
        Map<UUID, String> displayNames = userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        List<SusuGroupMemberDetail> memberDetails = members.stream()
                .map(m -> new SusuGroupMemberDetail(
                        m.getUserId(), displayNames.getOrDefault(m.getUserId(), "Unknown"),
                        m.getRotationPosition(), m.getStatus(), m.getJoinedAt()))
                .toList();

        List<SusuGroupContributionDetail> contributionDetails = List.of();
        if (group.getCurrentRoundNumber() != null) {
            Optional<SusuRoundEntity> currentRound =
                    roundRepo.findByGroupAndRoundNumber(groupId, group.getCurrentRoundNumber());
            if (currentRound.isPresent()) {
                contributionDetails = contributionRepo.findByRound(currentRound.get().getId()).stream()
                        .map(c -> new SusuGroupContributionDetail(
                                c.getMemberUserId(), c.getStatus(), c.getExpectedAmount(),
                                c.getCollectedAmount(), c.getPenaltyAmount(), c.isLate()))
                        .toList();
            }
        }

        long potBalance = group.getLedgerAccountId() != null
                ? paymentsClient.getLedgerAccountBalance(group.getLedgerAccountId())
                : 0L;

        return new AdminSusuGroupDetailResponse(
                group.getId(), group.getName(), group.getStatus(), group.getOrganiserUserId(),
                group.isFlaggedForReview(), group.getFlaggedAt(), group.getCurrentRoundNumber(),
                memberDetails, contributionDetails, potBalance);
    }

    @Transactional
    public void clearFlag(UUID groupId, UUID adminAccountId) {
        SusuGroupEntity group = groupRepo.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "SUSU_GROUP_NOT_FOUND: No susu group with id " + groupId));

        if (!group.isFlaggedForReview()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SUSU_GROUP_NOT_FLAGGED: This group is not currently flagged for review.");
        }

        groupRepo.clearFlag(groupId);

        auditRepo.save(AdminAuditActionEntity.create(
                adminAccountId, ACTION_TYPE_CLEAR_FLAG, TARGET_TYPE_SUSU_GROUP, groupId,
                null, null, Instant.now(clock)));
    }

    private FlaggedSusuGroupListItem toListItem(SusuGroupEntity group) {
        List<SusuContributionEntity> waived =
                contributionRepo.findWaivedPenaltyContributions(group.getId(), PageRequest.of(0, 1));

        Integer lastShortfallRoundNumber = null;
        UUID lastShortfallMemberUserId = null;
        Instant lastShortfallAt = null;

        if (!waived.isEmpty()) {
            SusuContributionEntity latest = waived.get(0);
            lastShortfallMemberUserId = latest.getMemberUserId();
            lastShortfallAt = latest.getCreatedAt();
            lastShortfallRoundNumber = roundRepo.findById(latest.getSusuRoundId())
                    .map(SusuRoundEntity::getRoundNumber)
                    .orElse(null);
        }

        long potBalance = group.getLedgerAccountId() != null
                ? paymentsClient.getLedgerAccountBalance(group.getLedgerAccountId())
                : 0L;

        return new FlaggedSusuGroupListItem(
                group.getId(), group.getName(), group.getOrganiserUserId(), group.getFlaggedAt(),
                lastShortfallRoundNumber, lastShortfallMemberUserId, lastShortfallAt, potBalance);
    }
}
