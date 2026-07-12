package com.stash.admin.service;

import com.stash.admin.api.dto.AdminKycSubmissionSummary;
import com.stash.admin.api.dto.AdminSusuMembershipSummary;
import com.stash.admin.api.dto.AdminTransactionSummary;
import com.stash.admin.api.dto.AdminUserDetailResponse;
import com.stash.admin.api.dto.AdminUserListItem;
import com.stash.admin.api.dto.AdminUserListResponse;
import com.stash.admin.api.dto.AdminVaultSummary;
import com.stash.admin.integration.IntegrationKycClient;
import com.stash.admin.integration.IntegrationPaymentsClient;
import com.stash.admin.integration.KycSubmissionRecord;
import com.stash.admin.integration.TransactionRecord;
import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.service.SusuMembershipService;
import com.stash.platform.user.api.dto.AdminUserSearchCriteria;
import com.stash.platform.user.api.dto.AdminUserView;
import com.stash.platform.user.service.UserService;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.service.VaultService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {

    private static final int RECENT_TRANSACTION_LIMIT = 10;

    private final UserService             userService;
    private final VaultService            vaultService;
    private final SusuMembershipService   susuMembershipService;
    private final IntegrationPaymentsClient paymentsClient;
    private final IntegrationKycClient    kycClient;

    public AdminUserService(UserService userService,
                            VaultService vaultService,
                            SusuMembershipService susuMembershipService,
                            IntegrationPaymentsClient paymentsClient,
                            IntegrationKycClient kycClient) {
        this.userService           = userService;
        this.vaultService          = vaultService;
        this.susuMembershipService = susuMembershipService;
        this.paymentsClient        = paymentsClient;
        this.kycClient             = kycClient;
    }

    public AdminUserListResponse search(AdminUserSearchCriteria criteria, Pageable pageable) {
        Page<AdminUserView> page = userService.searchForAdmin(criteria, pageable);
        List<AdminUserListItem> items = page.getContent().stream()
                .map(this::toListItem)
                .toList();
        return new AdminUserListResponse(
                items,
                page.getTotalElements(),
                page.getTotalPages(),
                pageable.getPageNumber(),
                pageable.getPageSize());
    }

    public AdminUserDetailResponse getDetail(UUID userId) {
        AdminUserView user = userService.getAdminViewById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));

        List<AdminVaultSummary> vaults = vaultService.listForOwner(userId).stream()
                .map(this::toVaultSummary).toList();

        List<AdminSusuMembershipSummary> susuMemberships = susuMembershipService
                .listActiveMembershipsForUser(userId).stream()
                .map(this::toSusuSummary).toList();

        List<AdminTransactionSummary> transactions = paymentsClient
                .getRecentTransactionsForUser(userId, RECENT_TRANSACTION_LIMIT).stream()
                .map(this::toTransactionSummary).toList();

        List<AdminKycSubmissionSummary> kycHistory = kycClient
                .getSubmissionHistoryForUser(userId).stream()
                .map(this::toKycSummary).toList();

        return new AdminUserDetailResponse(user, vaults, susuMemberships, transactions, kycHistory);
    }

    private AdminUserListItem toListItem(AdminUserView v) {
        return new AdminUserListItem(
                v.id(), v.displayName(), v.email(), v.phone(),
                v.kycStatus(), v.accountStatus(), v.subscriptionTier(),
                v.createdAt(), v.maskedGhanaCard());
    }

    private AdminVaultSummary toVaultSummary(VaultEntity v) {
        long balance = v.getLedgerAccountId() != null
                ? paymentsClient.getLedgerAccountBalance(v.getLedgerAccountId())
                : 0L;
        return new AdminVaultSummary(v.getId(), v.getName(), v.getVaultType(), v.getStatus(), balance);
    }

    private AdminSusuMembershipSummary toSusuSummary(SusuMembershipEntity m) {
        return new AdminSusuMembershipSummary(
                m.getSusuGroupId(),
                null,
                m.getRotationPosition(),
                m.getStatus());
    }

    private AdminTransactionSummary toTransactionSummary(TransactionRecord t) {
        return new AdminTransactionSummary(
                t.reference(), t.type(), t.amountPesewas(), t.status(), t.occurredAt());
    }

    private AdminKycSubmissionSummary toKycSummary(KycSubmissionRecord r) {
        return new AdminKycSubmissionSummary(
                r.submissionId(), r.status(), r.reviewPath(),
                r.decision(), r.decisionReason(), r.submittedAt(), r.decidedAt());
    }
}
