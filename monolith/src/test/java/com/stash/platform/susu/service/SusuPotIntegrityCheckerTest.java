package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SusuPotIntegrityCheckerTest {

    private final SusuContributionRepository contributionRepo =
            Mockito.mock(SusuContributionRepository.class);
    private final SusuGroupRepository groupRepo =
            Mockito.mock(SusuGroupRepository.class);
    private final SusuPotBalanceClient balanceClient =
            Mockito.mock(SusuPotBalanceClient.class);

    private final SusuPotIntegrityChecker checker = new SusuPotIntegrityChecker(
            contributionRepo, groupRepo, balanceClient, new SimpleMeterRegistry());

    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID ROUND_ID = UUID.randomUUID();
    private static final UUID POT_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(activeGroupWithPot()));
    }

    // ── Balanced pot ───────────────────────────────────────────────────

    @Test
    @DisplayName("balanced pot: check() passes silently")
    void balanced_pot_passes() {
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);
        when(balanceClient.getBalance(POT_ID)).thenReturn(120_000L);

        assertThatCode(() -> checker.check(GROUP_ID, ROUND_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("balanced pot: checkQuietly() returns true")
    void balanced_pot_checkquietly_true() {
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);
        when(balanceClient.getBalance(POT_ID)).thenReturn(120_000L);

        assertThat(checker.checkQuietly(GROUP_ID, ROUND_ID)).isTrue();
    }

    @Test
    @DisplayName("zero contributions + zero pot: balanced")
    void zero_both_balanced() {
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(0L);
        when(balanceClient.getBalance(POT_ID)).thenReturn(0L);

        assertThatCode(() -> checker.check(GROUP_ID, ROUND_ID))
                .doesNotThrowAnyException();
    }

    // ── Drift detected ────────────────────────────────────────────────

    @Test
    @DisplayName("pot balance > expected: throws SusuIntegrityCheckException")
    void pot_exceeds_expected_throws() {
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(100_000L);
        when(balanceClient.getBalance(POT_ID)).thenReturn(120_000L);

        assertThatThrownBy(() -> checker.check(GROUP_ID, ROUND_ID))
                .isInstanceOf(SusuIntegrityCheckException.class)
                .satisfies(ex -> {
                    var e = (SusuIntegrityCheckException) ex;
                    assertThat(e.getExpectedPesewas()).isEqualTo(100_000L);
                    assertThat(e.getActualPesewas()).isEqualTo(120_000L);
                    assertThat(e.getMessage()).contains("[P0]");
                    assertThat(e.getMessage()).contains("group=" + GROUP_ID);
                });
    }

    @Test
    @DisplayName("pot balance < expected: throws SusuIntegrityCheckException")
    void pot_below_expected_throws() {
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);
        when(balanceClient.getBalance(POT_ID)).thenReturn(100_000L);

        assertThatThrownBy(() -> checker.check(GROUP_ID, ROUND_ID))
                .isInstanceOf(SusuIntegrityCheckException.class)
                .satisfies(ex -> {
                    var e = (SusuIntegrityCheckException) ex;
                    assertThat(e.getExpectedPesewas()).isEqualTo(120_000L);
                    assertThat(e.getActualPesewas()).isEqualTo(100_000L);
                });
    }

    @Test
    @DisplayName("drift: checkQuietly() returns false (does not throw)")
    void drift_checkquietly_returns_false() {
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);
        when(balanceClient.getBalance(POT_ID)).thenReturn(100_000L);

        assertThat(checker.checkQuietly(GROUP_ID, ROUND_ID)).isFalse();
    }

    @Test
    @DisplayName("drift: Prometheus counter incremented")
    void drift_increments_counter() {
        var registry = new SimpleMeterRegistry();
        var localChecker = new SusuPotIntegrityChecker(
                contributionRepo, groupRepo, balanceClient, registry);

        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);
        when(balanceClient.getBalance(POT_ID)).thenReturn(100_000L);

        localChecker.checkQuietly(GROUP_ID, ROUND_ID);

        double count = registry.get("susu_pot_integrity_drift_count").counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("multiple drifts: counter increments per drift")
    void multiple_drifts_increment_counter() {
        var registry = new SimpleMeterRegistry();
        var localChecker = new SusuPotIntegrityChecker(
                contributionRepo, groupRepo, balanceClient, registry);

        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);
        when(balanceClient.getBalance(POT_ID)).thenReturn(100_000L);

        localChecker.checkQuietly(GROUP_ID, ROUND_ID);
        localChecker.checkQuietly(GROUP_ID, ROUND_ID);
        localChecker.checkQuietly(GROUP_ID, ROUND_ID);

        double count = registry.get("susu_pot_integrity_drift_count").counter().count();
        assertThat(count).isEqualTo(3.0);
    }

    // ── Edge cases ────────────────────────────────────────────────────

    @Test
    @DisplayName("group not found: check skips silently without throwing")
    void group_not_found_skips() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> checker.check(GROUP_ID, ROUND_ID))
                .doesNotThrowAnyException();
        verifyNoInteractions(balanceClient);
    }

    @Test
    @DisplayName("group has no ledger account (not yet activated): skips silently")
    void no_ledger_account_skips() {
        SusuGroupEntity noAccount = SusuGroupEntity.create(
                UUID.randomUUID(), "Circle", 20_000L, "MONTHLY", 6,
                "STSH1234", Instant.parse("2026-06-24T00:00:00Z"));
        setField(noAccount, "id", GROUP_ID);
        setField(noAccount, "status", "ACTIVE");
        // ledgerAccountId is null — not yet activated
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(noAccount));

        assertThatCode(() -> checker.check(GROUP_ID, ROUND_ID))
                .doesNotThrowAnyException();
        verifyNoInteractions(balanceClient);
    }

    @Test
    @DisplayName("Payments Service unavailable: throws SusuIntegrityCheckException")
    void payments_unavailable_throws() {
        when(contributionRepo.sumCollectedAmountForRound(ROUND_ID)).thenReturn(120_000L);
        when(balanceClient.getBalance(POT_ID))
                .thenThrow(new SusuIntegrityCheckException("Payments down"));

        assertThatThrownBy(() -> checker.check(GROUP_ID, ROUND_ID))
                .isInstanceOf(SusuIntegrityCheckException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private SusuGroupEntity activeGroupWithPot() {
        SusuGroupEntity g = SusuGroupEntity.create(
                UUID.randomUUID(), "Akua's Circle", 20_000L, "MONTHLY", 6,
                "STSH1234", Instant.parse("2026-06-24T00:00:00Z"));
        setField(g, "id",              GROUP_ID);
        setField(g, "status",          "ACTIVE");
        setField(g, "ledgerAccountId", POT_ID);
        return g;
    }

    private static void setField(Object obj, String name, Object value) {
        try {
            var f = findField(obj.getClass(), name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name)
            throws NoSuchFieldException {
        try { return c.getDeclaredField(name); }
        catch (NoSuchFieldException e) {
            if (c.getSuperclass() != null) return findField(c.getSuperclass(), name);
            throw e;
        }
    }
}
