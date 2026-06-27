package com.stash.platform.vault.service;

import com.stash.platform.vault.api.dto.VaultListItemResponse;
import com.stash.platform.vault.api.dto.VaultListResponse;
import com.stash.platform.vault.client.PaymentsBalanceClient;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VaultListServiceTest {

    private final VaultRepository       vaultRepo     = Mockito.mock(VaultRepository.class);
    private final PaymentsBalanceClient balanceClient = Mockito.mock(PaymentsBalanceClient.class);
    private final VaultListService      service       =
            new VaultListService(vaultRepo, balanceClient);

    private static final UUID   USER_ID = UUID.randomUUID();
    private static final String CORR    = "corr-list-001";

    @BeforeEach
    void setUp() {
        when(balanceClient.fetchBalance(any(), any())).thenReturn(Optional.of(50_000L));
    }

    // ── Empty list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("user with no vaults: returns empty list")
    void empty_list() {
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of());

        VaultListResponse result = service.listVaults(USER_ID, false, CORR);

        assertThat(result.vaults()).isEmpty();
        assertThat(result.totalCount()).isEqualTo(0);
        assertThat(result.balanceUnavailableCount()).isEqualTo(0);
        verifyNoInteractions(balanceClient);
    }

    // ── Single vault ──────────────────────────────────────────────────────

    @Test
    @DisplayName("single active vault: returned with balance")
    void single_vault_with_balance() {
        VaultEntity vault = standardVault("Emergency Fund", Instant.parse("2026-06-24T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of(vault));
        when(balanceClient.fetchBalance(vault.getLedgerAccountId(), CORR))
                .thenReturn(Optional.of(100_000L));

        VaultListResponse result = service.listVaults(USER_ID, false, CORR);

        assertThat(result.vaults()).hasSize(1);
        VaultListItemResponse item = result.vaults().get(0);
        assertThat(item.name()).isEqualTo("Emergency Fund");
        assertThat(item.balancePesewas()).isEqualTo(100_000L);
        assertThat(item.balanceCedis()).isEqualTo("1000.00");
        assertThat(result.balanceUnavailableCount()).isEqualTo(0);
    }

    // ── Multiple vaults ───────────────────────────────────────────────────

    @Test
    @DisplayName("multiple vaults: all returned with balances in created_at DESC order")
    void multiple_vaults_ordered_newest_first() {
        VaultEntity newer = standardVault("New Fund", Instant.parse("2026-06-24T09:00:00Z"));
        VaultEntity older = standardVault("Old Fund", Instant.parse("2026-06-01T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of(newer, older));
        when(balanceClient.fetchBalance(newer.getLedgerAccountId(), CORR))
                .thenReturn(Optional.of(20_000L));
        when(balanceClient.fetchBalance(older.getLedgerAccountId(), CORR))
                .thenReturn(Optional.of(10_000L));

        VaultListResponse result = service.listVaults(USER_ID, false, CORR);

        assertThat(result.vaults()).hasSize(2);
        assertThat(result.vaults().get(0).name()).isEqualTo("New Fund");
        assertThat(result.vaults().get(1).name()).isEqualTo("Old Fund");
        assertThat(result.vaults().get(0).balancePesewas()).isEqualTo(20_000L);
        assertThat(result.vaults().get(1).balancePesewas()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("ordering is stable across multiple calls with same data")
    void ordering_is_stable() {
        VaultEntity v1 = standardVault("V1", Instant.parse("2026-06-24T09:00:00Z"));
        VaultEntity v2 = standardVault("V2", Instant.parse("2026-06-23T09:00:00Z"));
        VaultEntity v3 = standardVault("V3", Instant.parse("2026-06-22T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of(v1, v2, v3));

        VaultListResponse r1 = service.listVaults(USER_ID, false, CORR);
        VaultListResponse r2 = service.listVaults(USER_ID, false, CORR);

        for (int i = 0; i < 3; i++) {
            assertThat(r1.vaults().get(i).name()).isEqualTo(r2.vaults().get(i).name());
        }
    }

    // ── include_closed ────────────────────────────────────────────────────

    @Test
    @DisplayName("include_closed=false: only ACTIVE vaults returned")
    void closed_excluded_by_default() {
        VaultEntity active = standardVault("Active", Instant.parse("2026-06-24T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of(active));

        VaultListResponse result = service.listVaults(USER_ID, false, CORR);

        assertThat(result.vaults()).hasSize(1);
        assertThat(result.vaults().get(0).name()).isEqualTo("Active");
        verify(vaultRepo).findVaultsForUser(USER_ID, false);
    }

    @Test
    @DisplayName("include_closed=true: CLOSED vault included")
    void closed_included_when_requested() {
        VaultEntity active = standardVault("Active", Instant.parse("2026-06-24T09:00:00Z"));
        VaultEntity closed = closedVault("Closed", Instant.parse("2026-06-01T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, true)).thenReturn(List.of(active, closed));
        when(balanceClient.fetchBalance(closed.getLedgerAccountId(), CORR))
                .thenReturn(Optional.of(0L));

        VaultListResponse result = service.listVaults(USER_ID, true, CORR);

        assertThat(result.vaults()).hasSize(2);
        assertThat(result.vaults()).anyMatch(v -> "CLOSED".equals(v.status()));
        verify(vaultRepo).findVaultsForUser(USER_ID, true);
    }

    // ── Graceful degradation ──────────────────────────────────────────────

    @Test
    @DisplayName("Payments Service unavailable: vault returned with null balance")
    void payments_unavailable_graceful_degradation() {
        VaultEntity vault = standardVault("Emergency Fund", Instant.parse("2026-06-24T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of(vault));
        when(balanceClient.fetchBalance(vault.getLedgerAccountId(), CORR))
                .thenReturn(Optional.empty());

        VaultListResponse result = service.listVaults(USER_ID, false, CORR);

        assertThat(result.vaults()).hasSize(1);
        assertThat(result.vaults().get(0).balancePesewas()).isNull();
        assertThat(result.vaults().get(0).balanceCedis()).isNull();
        assertThat(result.balanceUnavailableCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("one vault balance fails: others still returned with correct balances")
    void partial_balance_failure_others_unaffected() {
        VaultEntity v1 = standardVault("V1", Instant.parse("2026-06-24T09:00:00Z"));
        VaultEntity v2 = standardVault("V2", Instant.parse("2026-06-23T09:00:00Z"));
        VaultEntity v3 = standardVault("V3", Instant.parse("2026-06-22T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of(v1, v2, v3));
        when(balanceClient.fetchBalance(v1.getLedgerAccountId(), CORR)).thenReturn(Optional.of(30_000L));
        when(balanceClient.fetchBalance(v2.getLedgerAccountId(), CORR)).thenReturn(Optional.empty());
        when(balanceClient.fetchBalance(v3.getLedgerAccountId(), CORR)).thenReturn(Optional.of(10_000L));

        VaultListResponse result = service.listVaults(USER_ID, false, CORR);

        assertThat(result.vaults()).hasSize(3);
        assertThat(result.vaults().get(0).balancePesewas()).isEqualTo(30_000L);
        assertThat(result.vaults().get(1).balancePesewas()).isNull();
        assertThat(result.vaults().get(2).balancePesewas()).isEqualTo(10_000L);
        assertThat(result.balanceUnavailableCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Payments Service throws: vault returned with null balance, no exception")
    void payments_throws_returns_null_balance() {
        VaultEntity vault = standardVault("Fund", Instant.parse("2026-06-24T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of(vault));
        when(balanceClient.fetchBalance(any(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> service.listVaults(USER_ID, false, CORR))
                .doesNotThrowAnyException();

        VaultListResponse result = service.listVaults(USER_ID, false, CORR);
        assertThat(result.vaults().get(0).balancePesewas()).isNull();
    }

    // ── Zero balance ──────────────────────────────────────────────────────

    @Test
    @DisplayName("zero balance returned as 0 pesewas and 0.00 cedis — not null")
    void zero_balance_not_null() {
        VaultEntity vault = standardVault("New Vault", Instant.parse("2026-06-24T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of(vault));
        when(balanceClient.fetchBalance(vault.getLedgerAccountId(), CORR))
                .thenReturn(Optional.of(0L));

        VaultListResponse result = service.listVaults(USER_ID, false, CORR);

        assertThat(result.vaults().get(0).balancePesewas()).isEqualTo(0L);
        assertThat(result.vaults().get(0).balanceCedis()).isEqualTo("0.00");
    }

    // ── EARLY_EXIT_PENDING ────────────────────────────────────────────────

    @Test
    @DisplayName("EARLY_EXIT_PENDING vault included in default response")
    void early_exit_pending_included() {
        VaultEntity earlyExit = lockedVaultEarlyExitPending(
                "Locked Fund", Instant.parse("2026-06-24T09:00:00Z"));
        when(vaultRepo.findVaultsForUser(USER_ID, false)).thenReturn(List.of(earlyExit));

        VaultListResponse result = service.listVaults(USER_ID, false, CORR);

        assertThat(result.vaults()).hasSize(1);
        assertThat(result.vaults().get(0).status()).isEqualTo("EARLY_EXIT_PENDING");
        assertThat(result.vaults().get(0).earlyExitInProgress()).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static VaultEntity standardVault(String name, Instant createdAt) {
        return VaultEntity.createStandard(USER_ID, name, UUID.randomUUID(), createdAt);
    }

    private static VaultEntity closedVault(String name, Instant createdAt) {
        VaultEntity v = VaultEntity.createStandard(USER_ID, name, UUID.randomUUID(), createdAt);
        setField(v, "status", "CLOSED");
        return v;
    }

    private static VaultEntity lockedVaultEarlyExitPending(String name, Instant createdAt) {
        VaultEntity v = VaultEntity.createLocked(USER_ID, name, UUID.randomUUID(),
                Instant.parse("2028-01-01T00:00:00Z"), null, null, createdAt);
        setField(v, "status", "EARLY_EXIT_PENDING");
        setField(v, "earlyExitInProgress", true);
        return v;
    }

    private static void setField(Object obj, String fieldName, Object value) {
        try {
            var f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
