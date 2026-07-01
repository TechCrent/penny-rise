package com.stash.admin.api.dto;

import com.stash.platform.user.api.dto.AdminUserView;

import java.util.List;

public record AdminUserDetailResponse(
        AdminUserView                    user,
        List<AdminVaultSummary>          vaults,
        List<AdminSusuMembershipSummary> activeSusuMemberships,
        List<AdminTransactionSummary>    recentTransactions,
        List<AdminKycSubmissionSummary>  kycSubmissionHistory
) {}
