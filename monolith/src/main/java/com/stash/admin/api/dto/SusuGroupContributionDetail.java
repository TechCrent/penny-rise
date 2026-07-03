package com.stash.admin.api.dto;

import java.util.UUID;

public record SusuGroupContributionDetail(
        UUID memberUserId, String status, long expectedAmount, Long collectedAmount,
        long penaltyAmount, boolean isLate) {}
