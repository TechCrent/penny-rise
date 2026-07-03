package com.stash.platform.vault.service;

import com.stash.platform.subscription.policy.SubscriptionPolicy;
import com.stash.platform.subscription.service.SubscriptionLimitChecker;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.api.dto.VaultDepositRequest;
import com.stash.platform.vault.api.dto.VaultDepositResponse;
import com.stash.platform.vault.client.PaymentsDepositClient;
import com.stash.platform.vault.client.PaymentsServiceException;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class VaultDepositServiceTest {

    private final VaultRepository          vaultRepo      = Mockito.mock(VaultRepository.class);
    private final UserRepository           userRepo       = Mockito.mock(UserRepository.class);
    private final PaymentsDepositClient    paymentsClient = Mockito.mock(PaymentsDepositClient.class);
    private final SubscriptionLimitChecker subscriptionLimitChecker =
            new SubscriptionLimitChecker(new SubscriptionPolicy());
    private final VaultDepositService   service =
            new VaultDepositService(vaultRepo, userRepo, paymentsClient, subscriptionLimitChecker);

    private static final UUID   USER_ID   = UUID.randomUUID();
    private static final UUID   VAULT_ID  = UUID.randomUUID();
    private static final UUID   LEDGER_ID = UUID.randomUUID();
    private static final String CORR      = "corr-deposit-001";
    private static final String IDEM_KEY  = "idem-deposit-001";

    @BeforeEach
    void setUp() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(activeVault()));
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(paymentsClient.initiateDeposit(any(), any(), any(), anyLong(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PaymentsDepositClient.DepositResult(
                        "STSH-202606-DEP001", "Dial *170#", "pay_ref_001", "PENDING"));
    }

    // ── Happy paths ───────────────────────────────────────────────────────

    @Test
    @DisplayName("MOMO deposit: initiates and returns 202 PENDING with authorisation info")
    void momo_deposit_happy_path() {
        VaultDepositResponse result =
                service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY);

        assertThat(result.transactionReference()).isEqualTo("STSH-202606-DEP001");
        assertThat(result.authorisationUrl()).isEqualTo("Dial *170#");
        assertThat(result.paystackReference()).isEqualTo("pay_ref_001");
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("deposit into EARLY_EXIT_PENDING vault is allowed")
    void early_exit_pending_vault_allowed() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(earlyExitPendingVault()));

        assertThatCode(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("payments client called with correct ledger_account_id and user_id")
    void payments_client_called_with_correct_parameters() {
        service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY);

        verify(paymentsClient).initiateDeposit(
                eq(USER_ID),
                eq("akua@stash.test"),
                eq(LEDGER_ID),
                eq(10_000L),
                eq("MOMO"),
                eq("0241234567"),
                eq("mtn"),
                eq(VAULT_ID),
                eq(CORR),
                eq(IDEM_KEY)
        );
    }

    // ── Ownership and status validation ───────────────────────────────────

    @Test
    @DisplayName("vault not found returns 404")
    void vault_not_found_returns_404() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("vault not owned by user returns 403")
    void wrong_user_returns_403() {
        when(vaultRepo.findById(VAULT_ID))
                .thenReturn(Optional.of(vaultOwnedBy(UUID.randomUUID())));

        assertThatThrownBy(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("closed vault returns 409 with VAULT_CLOSED")
    void closed_vault_returns_409() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(closedVault()));

        assertThatThrownBy(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("VAULT_CLOSED");
                });
    }

    @Test
    @DisplayName("soft-deleted vault returns 409")
    void soft_deleted_vault_returns_409() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(softDeletedVault()));

        assertThatThrownBy(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    @DisplayName("FROZEN vault returns 422 with VAULT_FROZEN (v0.5-030)")
    void frozen_vault_returns_422() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(frozenVault()));

        assertThatThrownBy(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> {
                    var e = (StashApiException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VAULT_FROZEN);
                });
    }

    // ── Request validation ────────────────────────────────────────────────

    @Test
    @DisplayName("missing mobile_number for MOMO returns 422")
    void missing_mobile_number_returns_422() {
        var req = new VaultDepositRequest(10_000L, "MOMO", null, "mtn");

        assertThatThrownBy(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, req, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("invalid payment_method returns 422")
    void invalid_payment_method_returns_422() {
        var req = new VaultDepositRequest(10_000L, "CASH", null, null);

        assertThatThrownBy(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, req, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    // ── Payments Service failure propagation ──────────────────────────────

    @Test
    @DisplayName("Payments 422 (insufficient balance) propagated as-is")
    void payments_422_propagated() {
        when(paymentsClient.initiateDeposit(any(), any(), any(), anyLong(),
                any(), any(), any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException("Insufficient balance", 422,
                        new RuntimeException()));

        assertThatThrownBy(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("Payments 503 (unavailable) returned as 502 to caller")
    void payments_5xx_returned_as_502() {
        when(paymentsClient.initiateDeposit(any(), any(), any(), anyLong(),
                any(), any(), any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException("Gateway timeout", 503,
                        new RuntimeException()));

        assertThatThrownBy(() ->
                service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(BAD_GATEWAY));
    }

    @Test
    @DisplayName("vault row NOT affected when Payments call fails")
    void vault_not_mutated_on_payments_failure() {
        when(paymentsClient.initiateDeposit(any(), any(), any(), anyLong(),
                any(), any(), any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException("timeout", 503, new RuntimeException()));

        try {
            service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY);
        } catch (ResponseStatusException ignored) {}

        verify(vaultRepo, never()).save(any());
    }

    // ── Idempotent retry ──────────────────────────────────────────────────

    @Test
    @DisplayName("same idempotency key forwarded to Payments — Payments handles dedup")
    void idempotency_key_forwarded_to_payments() {
        service.initiateDeposit(VAULT_ID, USER_ID, momoRequest(), CORR, IDEM_KEY);

        verify(paymentsClient).initiateDeposit(
                any(), any(), any(), anyLong(), any(), any(), any(), any(), any(),
                eq(IDEM_KEY));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private VaultEntity activeVault() {
        return VaultEntity.createStandard(USER_ID, "Emergency Fund", LEDGER_ID,
                Instant.parse("2026-06-24T09:00:00Z"));
    }

    private VaultEntity vaultOwnedBy(UUID ownerId) {
        return VaultEntity.createStandard(ownerId, "Other Vault", LEDGER_ID,
                Instant.parse("2026-06-24T09:00:00Z"));
    }

    private VaultEntity earlyExitPendingVault() {
        VaultEntity v = VaultEntity.createLocked(USER_ID, "Locked Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
        setField(v, "status", "EARLY_EXIT_PENDING");
        setField(v, "earlyExitInProgress", true);
        return v;
    }

    private VaultEntity closedVault() {
        VaultEntity v = VaultEntity.createStandard(USER_ID, "Closed", LEDGER_ID,
                Instant.parse("2026-06-01T00:00:00Z"));
        setField(v, "status", "CLOSED");
        return v;
    }

    private VaultEntity frozenVault() {
        VaultEntity v = VaultEntity.createStandard(USER_ID, "Frozen", LEDGER_ID,
                Instant.parse("2026-06-01T00:00:00Z"));
        setField(v, "status", "FROZEN");
        return v;
    }

    private VaultEntity softDeletedVault() {
        VaultEntity v = VaultEntity.createStandard(USER_ID, "Deleted", LEDGER_ID,
                Instant.parse("2026-06-01T00:00:00Z"));
        setField(v, "deletedAt", Instant.parse("2026-06-10T00:00:00Z"));
        return v;
    }

    private static User user() {
        User u = new User();
        u.setEmail("akua@stash.test");
        return u;
    }

    private static VaultDepositRequest momoRequest() {
        return new VaultDepositRequest(10_000L, "MOMO", "0241234567", "mtn");
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
