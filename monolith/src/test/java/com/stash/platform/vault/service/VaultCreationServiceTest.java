package com.stash.platform.vault.service;

import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.api.dto.CreateVaultRequest;
import com.stash.platform.vault.client.PaymentsServiceClient;
import com.stash.platform.vault.client.PaymentsServiceException;
import com.stash.platform.vault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class VaultCreationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final VaultRepository       vaultRepo      = Mockito.mock(VaultRepository.class);
    private final UserRepository        userRepo       = Mockito.mock(UserRepository.class);
    private final PaymentsServiceClient paymentsClient = Mockito.mock(PaymentsServiceClient.class);
    private final VaultCreationService  service =
            new VaultCreationService(vaultRepo, userRepo, paymentsClient, FIXED_CLOCK);

    private static final UUID USER_ID      = UUID.randomUUID();
    private static final UUID LEDGER_ACCT  = UUID.randomUUID();
    private static final String CORR_ID    = "corr-vault-001";
    private static final String IDEM_KEY   = "idem-vault-001";

    @BeforeEach
    void setUp() {
        when(userRepo.lockUserRow(USER_ID)).thenReturn(USER_ID);
        when(userRepo.findByIdForVaultCreation(USER_ID))
                .thenReturn(Optional.of(approvedFreeUser()));
        when(vaultRepo.countByOwnerUserIdAndVaultType(USER_ID, "STANDARD")).thenReturn(0L);
        when(vaultRepo.countByOwnerUserIdAndVaultType(USER_ID, "LOCKED")).thenReturn(0L);
        when(paymentsClient.provisionVaultLedgerAccount(any(), any(), any(), any()))
                .thenReturn(LEDGER_ACCT);
        when(vaultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy paths ───────────────────────────────────────────────────────

    @Test
    @DisplayName("STANDARD vault: created and ledger account provisioned")
    void standard_vault_created() {
        var result = service.createVault(USER_ID, standardRequest(), CORR_ID, IDEM_KEY);

        assertThat(result.vaultType()).isEqualTo("STANDARD");
        assertThat(result.ledgerAccountId()).isEqualTo(LEDGER_ACCT);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("LOCKED vault with date only: unlock_condition_logic is null")
    void locked_vault_date_only() {
        var req = new CreateVaultRequest("University Fund", "LOCKED",
                Instant.parse("2028-06-24T00:00:00Z"), null, null);

        var result = service.createVault(USER_ID, req, CORR_ID, IDEM_KEY);

        assertThat(result.vaultType()).isEqualTo("LOCKED");
        assertThat(result.unlockAt()).isNotNull();
        assertThat(result.unlockAmount()).isNull();
        assertThat(result.unlockConditionLogic()).isNull();
    }

    @Test
    @DisplayName("LOCKED vault with amount only: unlock_condition_logic is null")
    void locked_vault_amount_only() {
        var req = new CreateVaultRequest("Car Fund", "LOCKED",
                null, 500_000L, null);

        var result = service.createVault(USER_ID, req, CORR_ID, IDEM_KEY);

        assertThat(result.unlockAmount()).isEqualTo(500_000L);
        assertThat(result.unlockAt()).isNull();
        assertThat(result.unlockConditionLogic()).isNull();
    }

    @Test
    @DisplayName("LOCKED vault with both conditions: unlock_condition_logic defaults to AND")
    void locked_vault_both_conditions_defaults_to_and() {
        var req = new CreateVaultRequest("House Fund", "LOCKED",
                Instant.parse("2028-06-24T00:00:00Z"), 5_000_000L, null);

        var result = service.createVault(USER_ID, req, CORR_ID, IDEM_KEY);

        assertThat(result.unlockConditionLogic()).isEqualTo("AND");
    }

    @Test
    @DisplayName("LOCKED vault with explicit OR logic: stored correctly")
    void locked_vault_explicit_or_logic() {
        var req = new CreateVaultRequest("Flex Fund", "LOCKED",
                Instant.parse("2028-06-24T00:00:00Z"), 5_000_000L, "OR");

        var result = service.createVault(USER_ID, req, CORR_ID, IDEM_KEY);

        assertThat(result.unlockConditionLogic()).isEqualTo("OR");
    }

    // ── Validation failures ───────────────────────────────────────────────

    @Test
    @DisplayName("LOCKED vault with no unlock conditions returns 422")
    void locked_no_conditions_returns_422() {
        var req = new CreateVaultRequest("Bad Locked", "LOCKED", null, null, null);

        assertThatThrownBy(() -> service.createVault(USER_ID, req, CORR_ID, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("STANDARD vault with unlock_at returns 422")
    void standard_with_unlock_conditions_returns_422() {
        var req = new CreateVaultRequest("Bad Standard", "STANDARD",
                Instant.parse("2028-06-24T00:00:00Z"), null, null);

        assertThatThrownBy(() -> service.createVault(USER_ID, req, CORR_ID, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("unlock_at in the past returns 422")
    void unlock_at_in_past_returns_422() {
        var req = new CreateVaultRequest("Past Fund", "LOCKED",
                Instant.parse("2020-01-01T00:00:00Z"), null, null);

        assertThatThrownBy(() -> service.createVault(USER_ID, req, CORR_ID, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    // ── KYC check ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("unverified KYC returns 403")
    void unverified_kyc_returns_403() {
        when(userRepo.findByIdForVaultCreation(USER_ID))
                .thenReturn(Optional.of(pendingKycUser()));

        assertThatThrownBy(() -> service.createVault(USER_ID, standardRequest(), CORR_ID, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    // ── Free-tier limits ──────────────────────────────────────────────────

    @Test
    @DisplayName("free tier at STANDARD limit returns 422 with VAULT_FREE_TIER_LIMIT_REACHED")
    void standard_limit_reached_returns_422() {
        when(vaultRepo.countByOwnerUserIdAndVaultType(USER_ID, "STANDARD"))
                .thenReturn((long) VaultCreationService.FREE_TIER_MAX_STANDARD);

        assertThatThrownBy(() -> service.createVault(USER_ID, standardRequest(), CORR_ID, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("VAULT_FREE_TIER_LIMIT_REACHED");
                });
    }

    @Test
    @DisplayName("free tier at LOCKED limit returns 422")
    void locked_limit_reached_returns_422() {
        when(vaultRepo.countByOwnerUserIdAndVaultType(USER_ID, "LOCKED"))
                .thenReturn((long) VaultCreationService.FREE_TIER_MAX_LOCKED);

        var req = new CreateVaultRequest("Locked Fund", "LOCKED",
                Instant.parse("2028-06-24T00:00:00Z"), null, null);

        assertThatThrownBy(() -> service.createVault(USER_ID, req, CORR_ID, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("premium user: no vault limit enforced")
    void premium_user_no_limit() {
        when(userRepo.findByIdForVaultCreation(USER_ID))
                .thenReturn(Optional.of(premiumApprovedUser()));
        when(vaultRepo.countByOwnerUserIdAndVaultType(USER_ID, "STANDARD"))
                .thenReturn(10L);  // well over free tier limit

        assertThatCode(() -> service.createVault(USER_ID, standardRequest(), CORR_ID, IDEM_KEY))
                .doesNotThrowAnyException();
    }

    // ── Payments Service failure ───────────────────────────────────────────

    @Test
    @DisplayName("Payments Service failure: returns 502, vault row NOT persisted")
    void payments_failure_vault_not_persisted() {
        when(paymentsClient.provisionVaultLedgerAccount(any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException("timeout", 503, new RuntimeException()));

        assertThatThrownBy(() -> service.createVault(USER_ID, standardRequest(), CORR_ID, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(BAD_GATEWAY));

        // Vault row must NOT have been saved
        verify(vaultRepo, never()).save(any());
    }

    // ── Concurrency ───────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent creation: second request sees updated count after first commits")
    void concurrent_creation_second_sees_limit() {
        // First request: count = 1 (one slot left); second (after first commits): count = 2 (at limit)
        when(vaultRepo.countByOwnerUserIdAndVaultType(USER_ID, "STANDARD"))
                .thenReturn(1L)
                .thenReturn(2L);

        // First succeeds
        assertThatCode(() -> service.createVault(USER_ID, standardRequest(), CORR_ID, "idem-001"))
                .doesNotThrowAnyException();

        // Second fails with limit reached
        assertThatThrownBy(() -> service.createVault(USER_ID, standardRequest(), CORR_ID, "idem-002"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static User approvedFreeUser() {
        User u = new User();
        u.setKycStatus(KycStatus.APPROVED);
        u.setSubscriptionTier(SubscriptionTier.FREE);
        return u;
    }

    private static User pendingKycUser() {
        User u = new User();
        u.setKycStatus(KycStatus.PENDING);
        u.setSubscriptionTier(SubscriptionTier.FREE);
        return u;
    }

    private static User premiumApprovedUser() {
        User u = new User();
        u.setKycStatus(KycStatus.APPROVED);
        u.setSubscriptionTier(SubscriptionTier.PREMIUM);
        return u;
    }

    private static CreateVaultRequest standardRequest() {
        return new CreateVaultRequest("Emergency Fund", "STANDARD", null, null, null);
    }
}
