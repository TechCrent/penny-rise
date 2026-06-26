package com.stash.platform.vault.service;

import com.stash.platform.vault.domain.EarlyExitRequestEntity;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.EarlyExitRequestRepository;
import com.stash.platform.vault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

class VaultEarlyExitCancellationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final VaultRepository            vaultRepo   = Mockito.mock(VaultRepository.class);
    private final EarlyExitRequestRepository requestRepo = Mockito.mock(EarlyExitRequestRepository.class);
    private final VaultEarlyExitCancellationService service =
            new VaultEarlyExitCancellationService(vaultRepo, requestRepo, FIXED_CLOCK);

    private static final UUID   USER_ID   = UUID.randomUUID();
    private static final UUID   VAULT_ID  = UUID.randomUUID();
    private static final UUID   LEDGER_ID = UUID.randomUUID();
    private static final String CORR      = "corr-cancel-001";

    @BeforeEach
    void setUp() {
        when(vaultRepo.findById(VAULT_ID))
                .thenReturn(Optional.of(earlyExitPendingVault()));
        when(requestRepo.findPendingByVaultId(VAULT_ID))
                .thenReturn(Optional.of(pendingRequest()));
        when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(vaultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancellation: request marked CANCELLED, vault restored to ACTIVE")
    void happy_cancellation() {
        service.cancelEarlyExit(VAULT_ID, USER_ID, CORR);

        // Vault restored to ACTIVE
        ArgumentCaptor<VaultEntity> vaultCaptor = ArgumentCaptor.forClass(VaultEntity.class);
        verify(vaultRepo).save(vaultCaptor.capture());
        VaultEntity saved = vaultCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.isEarlyExitInProgress()).isFalse();

        // Request marked CANCELLED with resolved_at
        ArgumentCaptor<EarlyExitRequestEntity> reqCaptor =
                ArgumentCaptor.forClass(EarlyExitRequestEntity.class);
        verify(requestRepo).save(reqCaptor.capture());
        EarlyExitRequestEntity savedReq = reqCaptor.getValue();
        assertThat(savedReq.getStatus()).isEqualTo("CANCELLED");
        assertThat(savedReq.getResolvedAt())
                .isEqualTo(Instant.parse("2026-06-24T10:00:00Z"));
    }

    @Test
    @DisplayName("cancellation returns without exception (204 NO_CONTENT)")
    void happy_cancellation_no_exception() {
        assertThatCode(() -> service.cancelEarlyExit(VAULT_ID, USER_ID, CORR))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("both vault save and request save are called — atomic update")
    void both_saves_called_atomically() {
        service.cancelEarlyExit(VAULT_ID, USER_ID, CORR);

        InOrder inOrder = inOrder(requestRepo, vaultRepo);
        inOrder.verify(requestRepo).save(any(EarlyExitRequestEntity.class));
        inOrder.verify(vaultRepo).save(any(VaultEntity.class));
    }

    // ── Validation failures ───────────────────────────────────────────────

    @Test
    @DisplayName("vault not found returns 404")
    void vault_not_found_returns_404() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelEarlyExit(VAULT_ID, USER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("vault not owned by user returns 403")
    void wrong_user_returns_403() {
        when(vaultRepo.findById(VAULT_ID))
                .thenReturn(Optional.of(earlyExitVaultOwnedBy(UUID.randomUUID())));

        assertThatThrownBy(() -> service.cancelEarlyExit(VAULT_ID, USER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(FORBIDDEN));
    }

    @Test
    @DisplayName("no PENDING early-exit request returns 404 with VAULT_NO_PENDING_EARLY_EXIT")
    void no_pending_request_returns_404() {
        when(requestRepo.findPendingByVaultId(VAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelEarlyExit(VAULT_ID, USER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND);
                    assertThat(e.getReason()).contains("VAULT_NO_PENDING_EARLY_EXIT");
                });
    }

    @Test
    @DisplayName("already-completed request (release worker ran): 404 VAULT_NO_PENDING_EARLY_EXIT")
    void completed_request_returns_404() {
        // Release worker set request to COMPLETED — findPendingByVaultId returns empty
        when(requestRepo.findPendingByVaultId(VAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelEarlyExit(VAULT_ID, USER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND);
                    assertThat(e.getReason()).contains("VAULT_NO_PENDING_EARLY_EXIT");
                });

        // Neither vault nor request are written to
        verify(vaultRepo, never()).save(any());
        verify(requestRepo, never()).save(any());
    }

    @Test
    @DisplayName("already-cancelled request: same 404 path — no double cancellation")
    void already_cancelled_returns_404() {
        // Already cancelled — findPendingByVaultId returns empty (query filters on status=PENDING)
        when(requestRepo.findPendingByVaultId(VAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelEarlyExit(VAULT_ID, USER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    @Test
    @DisplayName("ACTIVE vault with no early-exit in progress: 404 (no PENDING request)")
    void active_vault_no_early_exit_returns_404() {
        when(vaultRepo.findById(VAULT_ID)).thenReturn(Optional.of(activeLockedVault()));
        when(requestRepo.findPendingByVaultId(VAULT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelEarlyExit(VAULT_ID, USER_ID, CORR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(NOT_FOUND));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private VaultEntity earlyExitPendingVault() {
        VaultEntity v = VaultEntity.createLocked(USER_ID, "Locked Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
        setField(v, "status", "EARLY_EXIT_PENDING");
        setField(v, "earlyExitInProgress", true);
        return v;
    }

    private VaultEntity earlyExitVaultOwnedBy(UUID ownerId) {
        VaultEntity v = VaultEntity.createLocked(ownerId, "Other Fund", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
        setField(v, "status", "EARLY_EXIT_PENDING");
        setField(v, "earlyExitInProgress", true);
        return v;
    }

    private VaultEntity activeLockedVault() {
        return VaultEntity.createLocked(USER_ID, "Active Locked", LEDGER_ID,
                Instant.parse("2028-01-01T00:00:00Z"), null, null,
                Instant.parse("2026-06-24T09:00:00Z"));
    }

    private EarlyExitRequestEntity pendingRequest() {
        return EarlyExitRequestEntity.create(
                VAULT_ID, USER_ID, "MEDICAL",
                10_000L, 500L, 9_500L,
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-24T09:00:00Z")
        );
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
