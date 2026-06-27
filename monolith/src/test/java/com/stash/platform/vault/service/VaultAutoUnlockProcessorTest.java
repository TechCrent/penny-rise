package com.stash.platform.vault.service;

import com.stash.platform.vault.client.PaymentsBalanceClient;
import com.stash.platform.vault.domain.VaultEntity;
import com.stash.platform.vault.event.VaultUnlockedApplicationEvent;
import com.stash.platform.vault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VaultAutoUnlockProcessorTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private final VaultRepository          vaultRepo      = Mockito.mock(VaultRepository.class);
    private final PaymentsBalanceClient    balanceClient  = Mockito.mock(PaymentsBalanceClient.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    private final VaultAutoUnlockProcessor processor =
            new VaultAutoUnlockProcessor(vaultRepo, balanceClient, eventPublisher, FIXED_CLOCK);

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID LEDGER_ID = UUID.randomUUID();
    private static final String CORR    = "corr-unlock-001";

    @BeforeEach
    void setUp() {
        when(vaultRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(50_000L));
    }

    // ── Date condition ────────────────────────────────────────────────────

    @Test
    @DisplayName("date condition met (unlock_by_date in the past): vault unlocked")
    void date_condition_met_unlocks() {
        VaultEntity vault = lockedVaultWithDate(Instant.parse("2026-06-23T00:00:00Z"));

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isTrue();
        assertThat(vault.getUnlockedAt()).isEqualTo(Instant.parse("2026-06-24T10:00:00Z"));
        assertThat(vault.getStatus()).isEqualTo("ACTIVE");
        verify(vaultRepo).save(vault);
    }

    @Test
    @DisplayName("date condition not yet met (unlock_by_date in the future): no unlock")
    void date_condition_not_met_no_unlock() {
        VaultEntity vault = lockedVaultWithDate(Instant.parse("2026-12-31T00:00:00Z"));

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isFalse();
        verify(vaultRepo, never()).save(any());
    }

    @Test
    @DisplayName("date condition met exactly at now (boundary): vault unlocked")
    void date_condition_met_at_exactly_now() {
        VaultEntity vault = lockedVaultWithDate(Instant.parse("2026-06-24T10:00:00Z"));

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isTrue();
    }

    // ── Amount condition ──────────────────────────────────────────────────

    @Test
    @DisplayName("amount condition met (balance >= target): vault unlocked")
    void amount_condition_met_unlocks() {
        VaultEntity vault = lockedVaultWithAmount(50_000L);   // target 50000
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(50_000L)); // exactly at target

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("amount condition not met (balance < target): no unlock")
    void amount_condition_not_met_no_unlock() {
        VaultEntity vault = lockedVaultWithAmount(100_000L);  // target 100000
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(50_000L)); // below target

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isFalse();
        verify(vaultRepo, never()).save(any());
    }

    @Test
    @DisplayName("amount condition: Payments balance unavailable — conservative no unlock")
    void amount_condition_balance_unavailable_no_unlock() {
        VaultEntity vault = lockedVaultWithAmount(50_000L);
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.empty());

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isFalse();
    }

    // ── Both conditions — AND logic ───────────────────────────────────────

    @Test
    @DisplayName("AND: both conditions met — unlocks")
    void and_both_conditions_met_unlocks() {
        VaultEntity vault = lockedVaultWithBoth(
                Instant.parse("2026-06-23T00:00:00Z"),  // date in past — met
                50_000L, "AND");
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(60_000L)); // above target — met

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("AND: date met but amount not met — no unlock")
    void and_date_met_amount_not_met_no_unlock() {
        VaultEntity vault = lockedVaultWithBoth(
                Instant.parse("2026-06-23T00:00:00Z"),  // date met
                100_000L, "AND");
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(50_000L)); // below target

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("AND: amount met but date not met — no unlock")
    void and_amount_met_date_not_met_no_unlock() {
        VaultEntity vault = lockedVaultWithBoth(
                Instant.parse("2027-01-01T00:00:00Z"),  // date not yet met
                50_000L, "AND");
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(60_000L)); // above target

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isFalse();
    }

    // ── Both conditions — OR logic ────────────────────────────────────────

    @Test
    @DisplayName("OR: only date met — unlocks")
    void or_only_date_met_unlocks() {
        VaultEntity vault = lockedVaultWithBoth(
                Instant.parse("2026-06-23T00:00:00Z"),  // date met
                100_000L, "OR");
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(50_000L)); // amount NOT met

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("OR: only amount met — unlocks")
    void or_only_amount_met_unlocks() {
        VaultEntity vault = lockedVaultWithBoth(
                Instant.parse("2027-01-01T00:00:00Z"),  // date NOT met
                50_000L, "OR");
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(60_000L)); // amount met

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("OR: neither condition met — no unlock")
    void or_neither_met_no_unlock() {
        VaultEntity vault = lockedVaultWithBoth(
                Instant.parse("2027-01-01T00:00:00Z"),  // date not met
                100_000L, "OR");
        when(balanceClient.fetchBalance(LEDGER_ID, CORR)).thenReturn(Optional.of(50_000L)); // amount not met

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isFalse();
    }

    // ── Idempotency ───────────────────────────────────────────────────────

    @Test
    @DisplayName("already unlocked vault (unlocked_at set): skipped idempotently")
    void already_unlocked_skipped() {
        VaultEntity vault = lockedVaultWithDate(Instant.parse("2026-06-23T00:00:00Z"));
        // Simulate already unlocked
        try {
            var f = VaultEntity.class.getDeclaredField("unlockedAt");
            f.setAccessible(true);
            f.set(vault, Instant.parse("2026-06-23T12:00:00Z"));
        } catch (Exception e) { throw new RuntimeException(e); }

        boolean result = processor.evaluate(vault, CORR);

        assertThat(result).isFalse();
        verify(vaultRepo, never()).save(any());
    }

    // ── Event emission ────────────────────────────────────────────────────

    @Test
    @DisplayName("vault.unlocked application event published on successful unlock")
    void event_published_on_unlock() {
        VaultEntity vault = lockedVaultWithDate(Instant.parse("2026-06-23T00:00:00Z"));

        processor.evaluate(vault, CORR);

        ArgumentCaptor<VaultUnlockedApplicationEvent> eventCaptor =
                ArgumentCaptor.forClass(VaultUnlockedApplicationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        VaultUnlockedApplicationEvent event = eventCaptor.getValue();
        assertThat(event.getVaultId()).isEqualTo(vault.getId());
        assertThat(event.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(event.getUnlockedAt()).isEqualTo(Instant.parse("2026-06-24T10:00:00Z"));
        assertThat(event.getCorrelationId()).isEqualTo(CORR);
    }

    @Test
    @DisplayName("no event published when conditions not met")
    void no_event_when_not_unlocked() {
        VaultEntity vault = lockedVaultWithDate(Instant.parse("2026-12-31T00:00:00Z"));

        processor.evaluate(vault, CORR);

        verifyNoInteractions(eventPublisher);
    }

    // ── No penalty ────────────────────────────────────────────────────────

    @Test
    @DisplayName("unlock sets status=ACTIVE with unlocked_at — no balance movement")
    void natural_unlock_no_payments_call_for_penalty() {
        VaultEntity vault = lockedVaultWithDate(Instant.parse("2026-06-23T00:00:00Z"));

        processor.evaluate(vault, CORR);

        // Balance client was NOT called (date-only vault doesn't need balance check)
        verifyNoInteractions(balanceClient);
        // Status is ACTIVE; vault_type stays LOCKED (permanent classification)
        assertThat(vault.getStatus()).isEqualTo("ACTIVE");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private VaultEntity lockedVaultWithDate(Instant unlockByDate) {
        return VaultEntity.createLocked(USER_ID, "Date Vault", LEDGER_ID,
                unlockByDate, null, null,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private VaultEntity lockedVaultWithAmount(long targetAmount) {
        return VaultEntity.createLocked(USER_ID, "Amount Vault", LEDGER_ID,
                null, targetAmount, null,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private VaultEntity lockedVaultWithBoth(Instant unlockByDate, long targetAmount, String logic) {
        return VaultEntity.createLocked(USER_ID, "Both Vault", LEDGER_ID,
                unlockByDate, targetAmount, logic,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
