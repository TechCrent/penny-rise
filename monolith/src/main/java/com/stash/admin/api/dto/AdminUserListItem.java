package com.stash.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminUserListItem(
        UUID    id,
        String  displayName,
        String  email,
        String  phone,
        String  kycStatus,
        String  accountStatus,
        String  subscriptionTier,
        Instant createdAt,
        String  maskedGhanaCard
) {}
