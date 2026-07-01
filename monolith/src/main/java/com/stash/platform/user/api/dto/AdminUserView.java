package com.stash.platform.user.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Flattened, admin-facing view of a user. Ghana Card is pre-masked by
 * {@link com.stash.shared.masking.GhanaCardMasker} before this record is
 * constructed. No raw PII escapes this record.
 */
public record AdminUserView(
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
