package com.stash.platform.vault.service;

import com.stash.platform.vault.client.PaymentsBalanceClient;
import com.stash.platform.vault.client.PaymentsServiceException;
import com.stash.platform.vault.client.PaymentsTransferClient;
import com.stash.platform.vault.client.PaymentsWithdrawalClient;
import com.stash.platform.vault.domain.EarlyExitRequestEntity;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.EarlyExitRequestRepository;
import com.stash.platform.vault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EarlyExitReleaseProcessorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-27T10:00:00Z"), ZoneOffset.UTC);
    private static final String FEE_REVENUE_ID = "00000000-0000-0000-0000-000000000002";

    private final VaultRepository            vaultRepo        = Mockito.mock(VaultRepository.class);
    private final EarlyExitRequestRepository requestRepo      = Mockito.mock(EarlyExitRequestRepository.class);
    private final PaymentsBalanceClient      balanceClient    = Mockito.mock(PaymentsBalanceClient.class);
    private final PaymentsTransferClient     transferClient   = Mockito.mock(PaymentsTransferClient.class);
    private final PaymentsWithdrawalClient   withdrawalClient = Mockito.mock(PaymentsWithdrawalClient.class);
    private final EarlyExitReleaseMetrics    metrics          = Mockito.mock(EarlyExitReleaseMetrics.class);

    private final EarlyExitReleaseProcessor processor = new EarlyExitReleaseProcessor(
            vaultRepo, requestRepo, balanceClient, transferClient, withdrawalClient,
            metrics, FIXED_CLOCK, FEE_REVENUE_ID);

    private static final UUID   USER_ID   = UUID.randomUUID();
    private static final UUID   VAULT_ID  = UUID.randomUUID();
    private static final UUID   LEDGER_ID = UUID.randomUUID();
    private static final UUID   REQUEST_ID = UUID.randomUUID();
    private static final String CORR      = "corr-release-001";

    @BeforeEach
    void setUp() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(earlyExitVault()));
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(10_000L));
        when(transferClient.transfer(any(), any(), anyLong(), any(), any(), any(), any(), any()))
                .thenReturn("STSH-202606-PENALTY01");
        when(withdrawalClient.initiateWithdrawal(any(), any(), any(), any(), anyLong(),
                any(), any(), any(), any(), any()))
                .thenReturn(new PaymentsWithdrawalClient.WithdrawalResult(
                        "STSH-202606-WD001", "TRF_exit001", "PENDING"));
        when(vaultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy release: penalty transferred, withdrawal initiated, vault closed, request completed")
    void happy_release() {
        processor.process(pendingRequest(10_000L, 500L, 9_500L),
                "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        // Vault closed
        ArgumentCaptor<VaultEntity> vaultCaptor = ArgumentCaptor.forClass(VaultEntity.class);
        verify(vaultRepo).save(vaultCaptor.capture());
        assertThat(vaultCaptor.getValue().getStatus()).isEqualTo("CLOSED");
        assertThat(vaultCaptor.getValue().isEarlyExitInProgress()).isFalse();

        // Request completed
        ArgumentCaptor<EarlyExitRequestEntity> reqCaptor =
                ArgumentCaptor.forClass(EarlyExitRequestEntity.class);
        verify(requestRepo).save(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(reqCaptor.getValue().getAttempts()).isEqualTo(1);

        verify(metrics).recordReleased();
    }

    @Test
    @DisplayName("penalty transfer called with vault ledger account as source and FEE_REVENUE as dest")
    void penalty_transfer_correct_accounts() {
        processor.process(pendingRequest(10_000L, 500L, 9_500L),
                "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        verify(transferClient).transfer(
                eq(LEDGER_ID),                                  // source = vault
                eq(UUID.fromString(FEE_REVENUE_ID)),            // dest = FEE_REVENUE
                eq(500L),                                       // penalty amount
                eq("VAULT_EARLY_EXIT_PENALTY"),
                any(), any(), eq(CORR), any()
        );
    }

    @Test
    @DisplayName("withdrawal called with release amount from vault ledger account")
    void withdrawal_correct_amount_and_source() {
        processor.process(pendingRequest(10_000L, 500L, 9_500L),
                "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        verify(withdrawalClient).initiateWithdrawal(
                eq(USER_ID),
                eq("akua@stash.test"),
                eq("Akua Mensah"),
                eq(LEDGER_ID),          // source = vault
                eq(9_500L),             // 10000 current - 500 penalty
                eq("0241234567"),
                eq("mtn"),
                eq(VAULT_ID),
                eq(CORR),
                any()
        );
    }

    // ── Deposit during cool-off window — release amount recalculated ───────

    @Test
    @DisplayName("deposit during cool-off: release uses current balance minus penalty (not snapshot)")
    void deposit_during_cooloff_release_recalculated() {
        // Snapshot: balance=10000, penalty=500, release=9500
        // During cool-off: user deposited 5000 → current balance = 15000
        // Expected: penalty=500 (fixed), release=15000-500=14500 (NOT the snapshot 9500)
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(15_000L));

        processor.process(pendingRequest(10_000L, 500L, 9_500L),
                "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        // Penalty still 500 (snapshotted)
        verify(transferClient).transfer(
                any(), any(), eq(500L), any(), any(), any(), any(), any());

        // Withdrawal is 14500 = current(15000) - penalty(500)
        verify(withdrawalClient).initiateWithdrawal(
                any(), any(), any(), any(), eq(14_500L),
                any(), any(), any(), any(), any());
    }

    // ── Zero release amount ───────────────────────────────────────────────

    @Test
    @DisplayName("zero release amount: penalty transferred, no withdrawal, vault closed")
    void zero_release_no_withdrawal() {
        // Balance equals penalty — nothing left to release
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(500L));

        processor.process(pendingRequest(10_000L, 500L, 9_500L),
                "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        verify(transferClient).transfer(
                any(), any(), eq(500L), any(), any(), any(), any(), any());
        verifyNoInteractions(withdrawalClient);

        // Vault still closed
        ArgumentCaptor<VaultEntity> captor = ArgumentCaptor.forClass(VaultEntity.class);
        verify(vaultRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("zero penalty (empty vault): no penalty transfer, no withdrawal, vault closed")
    void zero_penalty_empty_vault() {
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(0L));

        processor.process(pendingRequest(0L, 0L, 0L),
                "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        verifyNoInteractions(transferClient);
        verifyNoInteractions(withdrawalClient);

        ArgumentCaptor<VaultEntity> captor = ArgumentCaptor.forClass(VaultEntity.class);
        verify(vaultRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLOSED");
    }

    // ── Failure paths ─────────────────────────────────────────────────────

    @Test
    @DisplayName("balance fetch failure: attempts incremented, vault NOT closed")
    void balance_fetch_failure_increments_attempts() {
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.empty());

        processor.process(pendingRequest(10_000L, 500L, 9_500L),
                "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        ArgumentCaptor<EarlyExitRequestEntity> reqCaptor =
                ArgumentCaptor.forClass(EarlyExitRequestEntity.class);
        verify(requestRepo).save(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(reqCaptor.getValue().getAttempts()).isEqualTo(1);
        verify(vaultRepo, never()).save(any());
        verify(metrics).recordFailed();
    }

    @Test
    @DisplayName("penalty transfer failure: attempts incremented, vault NOT closed, no withdrawal")
    void penalty_transfer_failure_increments_attempts() {
        when(transferClient.transfer(any(), any(), anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException("timeout", 503, null));

        processor.process(pendingRequest(10_000L, 500L, 9_500L),
                "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        verify(metrics).recordFailed();
        verifyNoInteractions(withdrawalClient);
        verify(vaultRepo, never()).save(any());
    }

    @Test
    @DisplayName("withdrawal failure after penalty: attempts incremented, vault NOT closed")
    void withdrawal_failure_increments_attempts() {
        when(withdrawalClient.initiateWithdrawal(any(), any(), any(), any(), anyLong(),
                any(), any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException("rejected", 422, null));

        processor.process(pendingRequest(10_000L, 500L, 9_500L),
                "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        verify(metrics).recordFailed();
        verify(vaultRepo, never()).save(any());

        ArgumentCaptor<EarlyExitRequestEntity> reqCaptor =
                ArgumentCaptor.forClass(EarlyExitRequestEntity.class);
        verify(requestRepo).save(reqCaptor.capture());
        assertThat(reqCaptor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("5th failure emits P0 alert; request stays PENDING for ops")
    void fifth_failure_emits_p0_alert() {
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.empty());

        // Simulate request already at attempt 4
        EarlyExitRequestEntity req = pendingRequest(10_000L, 500L, 9_500L);
        for (int i = 0; i < 4; i++) req.recordAttempt(Instant.parse("2026-06-27T08:00:00Z"));

        processor.process(req, "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        assertThat(req.getAttempts()).isEqualTo(5);
        assertThat(req.getStatus()).isEqualTo("PENDING");   // NOT completed/cancelled
        verify(metrics).recordP0Alert();
    }

    // ── Idempotency of penalty transfer on retry ──────────────────────────

    @Test
    @DisplayName("penalty idempotency key includes request ID — same on every retry")
    void penalty_transfer_idempotency_key_stable() {
        when(withdrawalClient.initiateWithdrawal(any(), any(), any(), any(), anyLong(),
                any(), any(), any(), any(), any()))
                .thenThrow(new PaymentsServiceException("down", 503, null));

        EarlyExitRequestEntity req = pendingRequest(10_000L, 500L, 9_500L);

        // First attempt — penalty transfer called with stable key
        processor.process(req, "0241234567", "mtn", "Akua Mensah", "akua@stash.test", CORR);

        ArgumentCaptor<String> idemCaptor = ArgumentCaptor.forClass(String.class);
        verify(transferClient).transfer(
                any(), any(), anyLong(), any(), any(), any(), any(), idemCaptor.capture());
        String keyFirstAttempt = idemCaptor.getValue();
        assertThat(keyFirstAttempt).contains(req.getId().toString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private VaultEntity earlyExitVault() {
        VaultEntity v = VaultEntity.createLocked(USER_ID, "Locked Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
        try {
            var s = VaultEntity.class.getDeclaredField("status");
            s.setAccessible(true);
            s.set(v, "EARLY_EXIT_PENDING");
            var e = VaultEntity.class.getDeclaredField("earlyExitInProgress");
            e.setAccessible(true);
            e.set(v, true);
        } catch (Exception e) { throw new RuntimeException(e); }
        return v;
    }

    private EarlyExitRequestEntity pendingRequest(long balance, long penalty, long release) {
        EarlyExitRequestEntity r = EarlyExitRequestEntity.create(
                VAULT_ID, USER_ID, "MEDICAL",
                balance, penalty, release,
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-24T10:00:00Z")
        );
        // @UuidGenerator only assigns the id at persist time; set it here so the
        // idempotency-key assertions have a stable, non-null request id.
        setField(r, "id", REQUEST_ID);
        return r;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
