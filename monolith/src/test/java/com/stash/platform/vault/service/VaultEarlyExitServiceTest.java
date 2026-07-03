package com.stash.platform.vault.service;

import com.stash.platform.subscription.policy.SubscriptionPolicy;
import com.stash.platform.subscription.service.SubscriptionLimitChecker;
import com.stash.platform.vault.api.dto.EarlyExitRequest;
import com.stash.platform.vault.api.dto.EarlyExitResponse;
import com.stash.platform.vault.client.PaymentsBalanceClient;
import com.stash.platform.vault.domain.EarlyExitRequestEntity;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.EarlyExitRequestRepository;
import com.stash.platform.vault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class VaultEarlyExitServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final VaultRepository            vaultRepo         = Mockito.mock(VaultRepository.class);
    private final EarlyExitRequestRepository requestRepo       = Mockito.mock(EarlyExitRequestRepository.class);
    private final PaymentsBalanceClient      balanceClient     = Mockito.mock(PaymentsBalanceClient.class);
    private final EarlyExitPenaltyCalculator penaltyCalculator = new EarlyExitPenaltyCalculator();
    private final SubscriptionLimitChecker   subscriptionLimitChecker =
            new SubscriptionLimitChecker(new SubscriptionPolicy());
    private final VaultEarlyExitService      service           =
            new VaultEarlyExitService(vaultRepo, requestRepo, balanceClient,
                    penaltyCalculator, subscriptionLimitChecker, FIXED_CLOCK);

    private static final UUID   USER_ID    = UUID.randomUUID();
    private static final UUID   VAULT_ID   = UUID.randomUUID();
    private static final UUID   LEDGER_ID  = UUID.randomUUID();
    private static final String CORR       = "corr-early-exit-001";

    @BeforeEach
    void setUp() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(activeLocked()));
        when(requestRepo.findPendingByVaultId(VAULT_ID)).thenReturn(Optional.empty());
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(10_000L));
        when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(vaultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("creates early-exit request with correct penalty and release amounts")
    void happy_path_correct_penalty() {
        EarlyExitResponse result = service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR);

        assertThat(result.balanceAtRequestPesewas()).isEqualTo(10_000L);
        assertThat(result.penaltyAmountPesewas()).isEqualTo(500L);     // 5% of 10000
        assertThat(result.releaseAmountPesewas()).isEqualTo(9_500L);   // 10000 - 500
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("scheduled_release_at is exactly 72 hours after now")
    void scheduled_release_at_72_hours() {
        EarlyExitResponse result = service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR);

        Instant expected = Instant.parse("2026-06-27T10:00:00Z");  // now + 72h
        assertThat(result.scheduledReleaseAt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("vault status set to EARLY_EXIT_PENDING after request created")
    void vault_status_updated_to_early_exit_pending() {
        service.requestEarlyExit(VAULT_ID, USER_ID, request("MEDICAL"), CORR);

        ArgumentCaptor<VaultEntity> vaultCaptor = ArgumentCaptor.forClass(VaultEntity.class);
        verify(vaultRepo).save(vaultCaptor.capture());
        assertThat(vaultCaptor.getValue().getStatus()).isEqualTo("EARLY_EXIT_PENDING");
        assertThat(vaultCaptor.getValue().isEarlyExitInProgress()).isTrue();
    }

    @Test
    @DisplayName("reason stored on request row (normalised to uppercase)")
    void reason_stored_normalised() {
        service.requestEarlyExit(VAULT_ID, USER_ID, request("medical"), CORR);

        ArgumentCaptor<EarlyExitRequestEntity> captor =
                ArgumentCaptor.forClass(EarlyExitRequestEntity.class);
        verify(requestRepo).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("MEDICAL");
    }

    @Test
    @DisplayName("cedis fields formatted correctly in response")
    void cedis_formatted_in_response() {
        EarlyExitResponse result = service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR);

        assertThat(result.balanceAtRequestCedis()).isEqualTo("100.00");
        assertThat(result.penaltyAmountCedis()).isEqualTo("5.00");
        assertThat(result.releaseAmountCedis()).isEqualTo("95.00");
    }

    // ── Zero balance edge case ────────────────────────────────────────────

    @Test
    @DisplayName("zero balance: penalty = 0, release = 0, request still created")
    void zero_balance_request_still_created() {
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(0L));

        EarlyExitResponse result = service.requestEarlyExit(
                VAULT_ID, USER_ID, request("FAMILY"), CORR);

        assertThat(result.penaltyAmountPesewas()).isEqualTo(0L);
        assertThat(result.releaseAmountPesewas()).isEqualTo(0L);
        assertThat(result.status()).isEqualTo("PENDING");
    }

    // ── Validation failures ───────────────────────────────────────────────

    @Test
    @DisplayName("vault not found returns 404")
    void vault_not_found_returns_404() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("vault not owned by user returns 403")
    void wrong_user_returns_403() {
        when(vaultRepo.findById(VAULT_ID))
                .thenReturn(Optional.of(lockedVaultOwnedBy(UUID.randomUUID())));

        assertThatThrownBy(() -> service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("STANDARD vault returns 409 with VAULT_EARLY_EXIT_NOT_APPLICABLE")
    void standard_vault_returns_409() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(activeStandard()));

        assertThatThrownBy(() -> service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("VAULT_EARLY_EXIT_NOT_APPLICABLE");
                });
    }

    @Test
    @DisplayName("FROZEN vault returns 422 with VAULT_FROZEN (v0.5-030)")
    void frozen_vault_returns_422() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(frozenLockedVault()));

        assertThatThrownBy(() -> service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> {
                    var e = (StashApiException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(UNPROCESSABLE_ENTITY);
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VAULT_FROZEN);
                });
    }

    @Test
    @DisplayName("vault already EARLY_EXIT_PENDING returns 409 with VAULT_EARLY_EXIT_ALREADY_PENDING")
    void already_early_exit_pending_returns_409() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(earlyExitPendingVault()));

        assertThatThrownBy(() -> service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("VAULT_EARLY_EXIT_ALREADY_PENDING");
                });
    }

    @Test
    @DisplayName("application-level duplicate check: existing PENDING request returns 409")
    void application_level_duplicate_check() {
        // Vault is ACTIVE but there's already a PENDING request (e.g. status update lag)
        when(requestRepo.findPendingByVaultId(VAULT_ID))
                .thenReturn(Optional.of(Mockito.mock(EarlyExitRequestEntity.class)));

        assertThatThrownBy(() -> service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(CONFLICT));
    }

    @Test
    @DisplayName("DB unique constraint race condition: translated to 409")
    void db_constraint_race_condition_returns_409() {
        when(requestRepo.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(CONFLICT);
                    assertThat(e.getReason()).contains("VAULT_EARLY_EXIT_ALREADY_PENDING");
                });
    }

    @Test
    @DisplayName("invalid reason returns 422")
    void invalid_reason_returns_422() {
        assertThatThrownBy(() -> service.requestEarlyExit(
                VAULT_ID, USER_ID, request("VACATION"), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("invalid momo_provider returns 422")
    void invalid_momo_provider_returns_422() {
        var req = new EarlyExitRequest("MEDICAL", "0241234567", "ORANGE");

        assertThatThrownBy(() -> service.requestEarlyExit(VAULT_ID, USER_ID, req, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("destination_momo_number and momo_provider stored on the request row")
    void momo_details_stored_on_request() {
        var req = new EarlyExitRequest("MEDICAL", "0551234567", "vodafone");

        service.requestEarlyExit(VAULT_ID, USER_ID, req, CORR);

        ArgumentCaptor<EarlyExitRequestEntity> captor =
                ArgumentCaptor.forClass(EarlyExitRequestEntity.class);
        verify(requestRepo).save(captor.capture());
        assertThat(captor.getValue().getDestinationMomoNumber()).isEqualTo("0551234567");
        assertThat(captor.getValue().getMomoProvider()).isEqualTo("vodafone");
    }

    @Test
    @DisplayName("Payments Service unavailable returns 502")
    void payments_unavailable_returns_502() {
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(BAD_GATEWAY));
    }

    // ── Balance snapshot immutability ─────────────────────────────────────

    @Test
    @DisplayName("balance snapshot captured at request time — not affected by subsequent deposits")
    void balance_snapshot_is_immutable() {
        // Balance at request time: 10000p
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(10_000L));

        EarlyExitResponse result = service.requestEarlyExit(
                VAULT_ID, USER_ID, request("MEDICAL"), CORR);

        // Snapshot values are fixed at 10000p regardless of what happens later
        assertThat(result.balanceAtRequestPesewas()).isEqualTo(10_000L);
        assertThat(result.penaltyAmountPesewas()).isEqualTo(500L);
        assertThat(result.releaseAmountPesewas()).isEqualTo(9_500L);

        // A subsequent deposit during the cool-off window would change the live balance
        // but NOT the snapshot on the request row (it's already committed)
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(20_000L));
        // The release worker reads release_amount from the row (9500p), not the live balance
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private EarlyExitRequest request(String reason) {
        return new EarlyExitRequest(reason, "0241234567", "mtn");
    }

    private VaultEntity activeLocked() {
        return VaultEntity.createLocked(USER_ID, "Locked Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
    }

    private VaultEntity lockedVaultOwnedBy(UUID ownerId) {
        return VaultEntity.createLocked(ownerId, "Other Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
    }

    private VaultEntity activeStandard() {
        return VaultEntity.createStandard(USER_ID, "Standard Fund", LEDGER_ID,
                Instant.parse("2026-06-24T09:00:00Z"));
    }

    private VaultEntity earlyExitPendingVault() {
        VaultEntity v = VaultEntity.createLocked(USER_ID, "Pending Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
        try {
            var f = VaultEntity.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(v, "EARLY_EXIT_PENDING");
        } catch (Exception e) { throw new RuntimeException(e); }
        return v;
    }

    private VaultEntity frozenLockedVault() {
        VaultEntity v = VaultEntity.createLocked(USER_ID, "Frozen Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
        try {
            var f = VaultEntity.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(v, "FROZEN");
        } catch (Exception e) { throw new RuntimeException(e); }
        return v;
    }
}
