package com.stash.admin.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminSusuGroupDetailResponse(
        UUID id, String name, String status, UUID organiserUserId,
        boolean flaggedForReview, Instant flaggedAt,
        Integer currentRoundNumber,
        List<SusuGroupMemberDetail> members,
        List<SusuGroupContributionDetail> currentRoundContributions,
        long potBalancePesewas) {}
