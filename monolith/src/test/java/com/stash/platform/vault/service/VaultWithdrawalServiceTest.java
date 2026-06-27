package com.stash.platform.vault.service;

import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.platform.vault.api.dto.VaultWithdrawalRequest;
import com.stash.platform.vault.api.dto.VaultWithdrawalResponse;
import com.stash.platform.vault.client.PaymentsServiceException;
import com.stash.platform.vault.client.PaymentsWithdrawalClient;
import com.stash.platform.vault.domain.VaultEntity;
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

class VaultWithdrawalServiceTest {

    private final VaultRepository          vaultRepo      = Mockito.mock(VaultRepository.class);
    private final UserRepository           userRepo       = Mockito.mock(UserRepository.class);
    private final PaymentsWithdrawalClient paymentsClient = Mockito.mock(PaymentsWithdrawalClient.class);
    private final VaultWithdrawalService   service =
            new VaultWithdrawalService(vaultRepo, userRepo, paymentsClient);

    private static final UUID   USER_ID   = UUID.randomUUID();
    private static final UUID   VAULT_ID  = UUID.randomUUID();
    private static final UUID   LEDGER_ID = UUID.randomUUID();
    private static final String CORR      = "corr-wd-001";
    private static final String IDEM_KEY  = "idem-wd-001";

    @BeforeEach
    void setUp() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(activeStandardVault()));
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(paymentsClient.initiateWithdrawal(any(), any(), any(), any(),
                anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(new PaymentsWithdrawalClient.WithdrawalResult(
                        "STSH-202606-WD001", "TRF_test001", "PENDING"));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("STANDARD vault withdrawal returns 202 PENDING")
    void standard_vault_withdrawal_succeeds() {
        VaultWithdrawalResponse result =
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY);

        assertThat(result.transactionReference()).isEqualTo("STSH-202606-WD001");
        assertThat(result.paystackTransferCode()).isEqualTo("TRF_test001");
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("correct ledger_account_id and user details forwarded to Payments")
    void correct_fields_forwarded_to_payments() {
        service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY);

        verify(paymentsClient).initiateWithdrawal(
                eq(USER_ID),
                eq("akua@stash.test"),
                eq("Akua Mensah"),
                eq(LEDGER_ID),
                eq(10_000L),
                eq("0241234567"),
                eq("mtn"),
                eq(VAULT_ID),
                eq(CORR),
                eq(IDEM_KEY)
        );
    }

    // ── LOCKED vault rejection (the hard business rule) ───────────────────

    @Test
    @DisplayName("LOCKED vault returns 409 with VAULT_WITHDRAWAL_NOT_PERMITTED")
    void locked_vault_returns_409() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(lockedVault()));

        assertThatThrownBy(() ->
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("VAULT_WITHDRAWAL_NOT_PERMITTED");
                });
    }

    @Test
    @DisplayName("LOCKED vault check happens before Payments call — no Payments call made")
    void locked_vault_no_payments_call() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(lockedVault()));

        try {
            service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY);
        } catch (ResponseStatusException ignored) {}

        verifyNoInteractions(paymentsClient);
    }

    @Test
    @DisplayName("LOCKED vault with EARLY_EXIT_PENDING status also rejected")
    void locked_early_exit_pending_also_rejected() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(lockedEarlyExitVault()));

        assertThatThrownBy(() ->
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    @DisplayName("naturally-unlocked LOCKED vault (unlockedAt set by auto-unlock worker) allows withdrawal")
    void naturally_unlocked_locked_vault_allows_withdrawal() {
        VaultEntity v = lockedVault();
        setField(v, "unlockedAt", Instant.parse("2026-06-24T08:00:00Z"));
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(v));

        VaultWithdrawalResponse result =
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY);

        assertThat(result.transactionReference()).isEqualTo("STSH-202606-WD001");
        verify(paymentsClient).initiateWithdrawal(
                any(), any(), any(), any(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("LOCKED vault still rejected when unlockedAt is null")
    void locked_vault_without_unlockedAt_still_rejected() {
        VaultEntity v = lockedVault();
        assertThat(v.getUnlockedAt()).isNull();
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(v));

        assertThatThrownBy(() ->
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    // ── Ownership and status ──────────────────────────────────────────────

    @Test
    @DisplayName("vault not found returns 404")
    void vault_not_found_returns_404() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("vault not owned by user returns 403")
    void wrong_user_returns_403() {
        when(vaultRepo.findById(VAULT_ID))
                .thenReturn(Optional.of(standardVaultOwnedBy(UUID.randomUUID())));

        assertThatThrownBy(() ->
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("closed STANDARD vault returns 409 with VAULT_CLOSED")
    void closed_standard_vault_returns_409() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(closedStandardVault()));

        assertThatThrownBy(() ->
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("VAULT_CLOSED");
                });
    }

    // ── Insufficient balance error translation ────────────────────────────

    @Test
    @DisplayName("Payments 422 PAYMENTS_INSUFFICIENT_BALANCE → 422 VAULT_INSUFFICIENT_BALANCE")
    void insufficient_balance_translated_to_vault_code() {
        when(paymentsClient.initiateWithdrawal(any(), any(), any(), any(),
                anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException(
                        "{\"code\":\"PAYMENTS_INSUFFICIENT_BALANCE\",\"message\":\"...\"}",
                        422, new RuntimeException()));

        assertThatThrownBy(() ->
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getReason()).contains("VAULT_INSUFFICIENT_BALANCE");
                });
    }

    // ── Other Payments errors ─────────────────────────────────────────────

    @Test
    @DisplayName("Payments 5xx surfaces as 502 to caller")
    void payments_5xx_surfaces_as_502() {
        when(paymentsClient.initiateWithdrawal(any(), any(), any(), any(),
                anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException("timeout", 503, new RuntimeException()));

        assertThatThrownBy(() ->
                service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(BAD_GATEWAY));
    }

    @Test
    @DisplayName("Payments failure: vault row never written to (readOnly service)")
    void vault_not_mutated_on_payments_failure() {
        when(paymentsClient.initiateWithdrawal(any(), any(), any(), any(),
                anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException("down", 503, new RuntimeException()));

        try {
            service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY);
        } catch (ResponseStatusException ignored) {}

        verify(vaultRepo, never()).save(any());
    }

    // ── Idempotent retry ──────────────────────────────────────────────────

    @Test
    @DisplayName("idempotency key forwarded to Payments — Payments handles dedup")
    void idempotency_key_forwarded() {
        service.initiateWithdrawal(VAULT_ID, USER_ID, request(), CORR, IDEM_KEY);

        verify(paymentsClient).initiateWithdrawal(
                any(), any(), any(), any(), anyLong(), any(), any(), any(), any(),
                eq(IDEM_KEY));
    }

    // ── MoMo provider validation ──────────────────────────────────────────

    @Test
    @DisplayName("invalid momo_provider returns 422")
    void invalid_momo_provider_returns_422() {
        var req = new VaultWithdrawalRequest(10_000L, "0241234567", "ORANGE");

        assertThatThrownBy(() ->
                service.initiateWithdrawal(VAULT_ID, USER_ID, req, CORR, IDEM_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static VaultEntity activeStandardVault() {
        return VaultEntity.createStandard(USER_ID, "Emergency Fund", LEDGER_ID,
                Instant.parse("2026-06-24T09:00:00Z"));
    }

    private static VaultEntity standardVaultOwnedBy(UUID ownerId) {
        return VaultEntity.createStandard(ownerId, "Other Fund", LEDGER_ID,
                Instant.parse("2026-06-24T09:00:00Z"));
    }

    private static VaultEntity lockedVault() {
        return VaultEntity.createLocked(USER_ID, "Locked Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
    }

    private static VaultEntity lockedEarlyExitVault() {
        VaultEntity v = VaultEntity.createLocked(USER_ID, "Early Exit Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
        setField(v, "status", "EARLY_EXIT_PENDING");
        return v;
    }

    private static VaultEntity closedStandardVault() {
        VaultEntity v = VaultEntity.createStandard(USER_ID, "Closed Fund", LEDGER_ID,
                Instant.parse("2026-06-01T00:00:00Z"));
        setField(v, "status", "CLOSED");
        return v;
    }

    private static User user() {
        User u = new User();
        u.setEmail("akua@stash.test");
        u.setDisplayName("Akua Mensah");
        return u;
    }

    private static VaultWithdrawalRequest request() {
        return new VaultWithdrawalRequest(10_000L, "0241234567", "mtn");
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
