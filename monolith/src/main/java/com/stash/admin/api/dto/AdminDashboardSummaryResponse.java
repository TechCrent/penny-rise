package com.stash.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminDashboardSummaryResponse(
        @JsonProperty("pending_kyc_count")      long pendingKycCount,
        @JsonProperty("flagged_accounts_count")  long flaggedAccountsCount,
        @JsonProperty("open_disputes_count")     long openDisputesCount,
        @JsonProperty("flagged_susu_groups_count") long flaggedSusuGroupsCount
) {}
