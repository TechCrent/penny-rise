package com.stash.admin.service;

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
import com.stash.shared.masking.GhanaCardMasker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AdminUserService")
class AdminUserServiceTest {

    private static final UUID   USER_ID         = UUID.fromString("018a0000-0000-7000-8000-000000000001");
    private static final String FULL_GHANA_CARD = "GHA-123456789-0";

    private final UserService               userService           = mock(UserService.class);
    private final VaultService              vaultService          = mock(VaultService.class);
    private final SusuMembershipService     susuMembershipService = mock(SusuMembershipService.class);
    private final IntegrationPaymentsClient paymentsClient        = mock(IntegrationPaymentsClient.class);
    private final IntegrationKycClient      kycClient             = mock(IntegrationKycClient.class);

    private final AdminUserService service = new AdminUserService(
            userService, vaultService, susuMembershipService, paymentsClient, kycClient);

    @Test
    @DisplayName("getDetail aggregates data from all sub-services")
    void happyDetail() {
        stubFullProfile();
        when(vaultService.listForOwner(USER_ID)).thenReturn(List.of(sampleVault()));
        when(susuMembershipService.listActiveMembershipsForUser(USER_ID)).thenReturn(List.of(sampleSusu()));
        when(paymentsClient.getRecentTransactionsForUser(USER_ID, 10)).thenReturn(List.of(sampleTransaction()));
        when(kycClient.getSubmissionHistoryForUser(USER_ID)).thenReturn(List.of(sampleKycSubmission()));

        var detail = service.getDetail(USER_ID);

        assertThat(detail).isNotNull();
        assertThat(detail.user().id()).isEqualTo(USER_ID);
        assertThat(detail.vaults()).hasSize(1);
        assertThat(detail.activeSusuMemberships()).hasSize(1);
        assertThat(detail.recentTransactions()).hasSize(1);
        assertThat(detail.kycSubmissionHistory()).hasSize(1);
    }

    @Test
    @DisplayName("detail with no vaults returns an empty list, not null or an error")
    void detailNoVaults() {
        stubFullProfile();
        when(vaultService.listForOwner(USER_ID)).thenReturn(List.of());
        when(susuMembershipService.listActiveMembershipsForUser(USER_ID)).thenReturn(List.of());
        when(paymentsClient.getRecentTransactionsForUser(USER_ID, 10)).thenReturn(List.of());
        when(kycClient.getSubmissionHistoryForUser(USER_ID)).thenReturn(List.of());

        var detail = service.getDetail(USER_ID);

        assertThat(detail.vaults()).isEmpty();
        assertThat(detail.activeSusuMemberships()).isEmpty();
        assertThat(detail.recentTransactions()).isEmpty();
        assertThat(detail.kycSubmissionHistory()).isEmpty();
    }

    @Test
    @DisplayName("getDetail throws 404 USER_NOT_FOUND when user does not exist")
    void detailNotFound() {
        when(userService.getAdminViewById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("search delegates to UserService and wraps result in AdminUserListResponse")
    void searchDelegatesToUserService() {
        var criteria = new AdminUserSearchCriteria("test", null, null);
        var pageable = PageRequest.of(0, 20);
        when(userService.searchForAdmin(eq(criteria), any())).thenReturn(Page.empty());

        var result = service.search(criteria, pageable);

        verify(userService).searchForAdmin(criteria, pageable);
        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("detail user Ghana Card is already masked when returned from UserService")
    void detailGhanaCardIsMasked() {
        stubFullProfile();
        when(vaultService.listForOwner(USER_ID)).thenReturn(List.of());
        when(susuMembershipService.listActiveMembershipsForUser(USER_ID)).thenReturn(List.of());
        when(paymentsClient.getRecentTransactionsForUser(USER_ID, 10)).thenReturn(List.of());
        when(kycClient.getSubmissionHistoryForUser(USER_ID)).thenReturn(List.of());

        var detail = service.getDetail(USER_ID);

        assertThat(detail.user().maskedGhanaCard())
                .isEqualTo(GhanaCardMasker.maskToLastFour(FULL_GHANA_CARD));
        assertThat(detail.user().maskedGhanaCard()).doesNotContain(FULL_GHANA_CARD);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void stubFullProfile() {
        var view = new AdminUserView(
                USER_ID, "Test User", "test@stash.app", "+233241234567",
                "APPROVED", "ACTIVE", "FREE", Instant.now(),
                GhanaCardMasker.maskToLastFour(FULL_GHANA_CARD));
        when(userService.getAdminViewById(USER_ID)).thenReturn(Optional.of(view));
    }

    private VaultEntity sampleVault() {
        return VaultEntity.createStandard(USER_ID, "Test Vault", UUID.randomUUID(), Instant.now());
    }

    private SusuMembershipEntity sampleSusu() {
        return SusuMembershipEntity.create(UUID.randomUUID(), USER_ID, Instant.now());
    }

    private TransactionRecord sampleTransaction() {
        return new TransactionRecord("REF-001", "PEER_TRANSFER", 10_000L, "COMPLETED", Instant.now());
    }

    private KycSubmissionRecord sampleKycSubmission() {
        return new KycSubmissionRecord(
                UUID.randomUUID(), "APPROVED", null, "APPROVED", null, Instant.now(), Instant.now());
    }
}
