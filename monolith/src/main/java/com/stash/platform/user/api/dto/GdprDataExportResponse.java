package com.stash.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stash.platform.susu.api.dto.SusuGroupListItemResponse;
import com.stash.platform.transaction.api.dto.UnifiedTransactionItem;

import java.time.Instant;
import java.util.List;

/**
 * "Download my data" bundle — everything monolith can already reach
 * through its existing internal clients/services for this user's own
 * resources. Deliberately excludes raw KYC document images (privacy/size;
 * KYC status is included, the underlying photos are not) and password
 * hashes (never exposed anywhere, per {@link UserProfileResponse}).
 */
public record GdprDataExportResponse(
        UserProfileResponse profile,
        List<GdprVaultSummary> vaults,
        @JsonProperty("susu_memberships") List<SusuGroupListItemResponse> susuMemberships,
        List<UnifiedTransactionItem> transactions,
        @JsonProperty("exported_at") Instant exportedAt
) {}
