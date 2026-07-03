package com.stash.platform.subscription.service;

import com.stash.platform.subscription.api.dto.FrozenSusuGroupPreview;
import com.stash.platform.subscription.api.dto.FrozenVaultPreview;
import com.stash.platform.subscription.domain.Subscription;
import com.stash.platform.subscription.policy.SubscriptionPolicy;
import com.stash.platform.subscription.repository.SubscriptionRepository;
import com.stash.platform.transfer.domain.MonthlyTransferQuotaEntity;
import com.stash.platform.transfer.repository.MonthlyTransferQuotaRepository;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.repository.RefreshTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.randomUUID();

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final MonthlyTransferQuotaRepository quotaRepository = mock(MonthlyTransferQuotaRepository.class);
    private final VaultFreezingService vaultFreezingService = mock(VaultFreezingService.class);
    private final SusuFreezingService susuFreezingService = mock(SusuFreezingService.class);
    private final SubscriptionPolicy policy = new SubscriptionPolicy();
    private final SubscriptionPaystackClient paystackClient = mock(SubscriptionPaystackClient.class);

    private final SubscriptionService service = new SubscriptionService(
            subscriptionRepository, userRepository, refreshTokenRepository, quotaRepository,
            vaultFreezingService, susuFreezingService, policy, paystackClient, FIXED_CLOCK);

    private Subscription freeSubscription() {
        Subscription s = Subscription.createInitialFree(USER_ID, Instant.parse("2026-01-01T00:00:00Z"));
        return s;
    }

    private Subscription premiumSubscription() {
        Subscription s = freeSubscription();
        s.activatePremium("sub_test_123", Instant.parse("2026-06-01T00:00:00Z"));
        return s;
    }

    @BeforeEach
    void setUp() {
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── getStatus ────────────────────────────────────────────────────────

    @Test
    @DisplayName("FREE user's free_transfers_remaining reflects quota already used this month")
    void statusReflectsQuotaUsage() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(freeSubscription()));
        MonthlyTransferQuotaEntity quota = MonthlyTransferQuotaEntity.create(USER_ID, 2026, 7);
        quota.consumeOneTransfer(SubscriptionPolicy.FREE_TRANSFERS_PER_MONTH);
        quota.consumeOneTransfer(SubscriptionPolicy.FREE_TRANSFERS_PER_MONTH);
        when(quotaRepository.findByUserAndMonth(USER_ID, 2026, 7)).thenReturn(Optional.of(quota));

        var status = service.getStatus(USER_ID);

        assertThat(status.tier()).isEqualTo("FREE");
        assertThat(status.freeTransfersRemaining()).isEqualTo(3); // 5 - 2 used
    }

    @Test
    @DisplayName("FREE user with no quota row yet this month gets the full free quota remaining")
    void statusWithNoQuotaRowYet() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(freeSubscription()));
        when(quotaRepository.findByUserAndMonth(USER_ID, 2026, 7)).thenReturn(Optional.empty());

        var status = service.getStatus(USER_ID);

        assertThat(status.freeTransfersRemaining()).isEqualTo(SubscriptionPolicy.FREE_TRANSFERS_PER_MONTH);
    }

    @Test
    @DisplayName("PREMIUM user's free_transfers_remaining is the PREMIUM monthly limit, quota not consulted")
    void statusForPremiumUser() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(premiumSubscription()));

        var status = service.getStatus(USER_ID);

        assertThat(status.tier()).isEqualTo("PREMIUM");
        assertThat(status.freeTransfersRemaining()).isEqualTo(SubscriptionPolicy.PREMIUM_TRANSFERS_PER_MONTH);
        verifyNoInteractions(quotaRepository);
    }

    // ── upgrade ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("happy upgrade: FREE -> PREMIUM, users.subscription_tier synced, sessions revoked")
    void happyUpgrade() {
        when(subscriptionRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(freeSubscription()));
        when(paystackClient.createTestSubscription(eq(USER_ID), anyString())).thenReturn("sub_test_new");

        var response = service.upgrade(USER_ID, "paystack_test_token");

        assertThat(response.tier()).isEqualTo("PREMIUM");
        verify(userRepository).syncSubscriptionTier(USER_ID, SubscriptionTier.PREMIUM);
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(USER_ID), eq("SUBSCRIPTION_TIER_CHANGED"), any());
    }

    @Test
    @DisplayName("upgrading an already-PREMIUM user throws SUBSCRIPTION_ALREADY_PREMIUM (409), no Paystack call")
    void upgradeAlreadyPremiumThrows() {
        when(subscriptionRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(premiumSubscription()));

        assertThatThrownBy(() -> service.upgrade(USER_ID, "token"))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> {
                    var e = (StashApiException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_ALREADY_PREMIUM);
                });

        verifyNoInteractions(paystackClient);
        verify(subscriptionRepository, never()).save(any());
    }

    // ── downgrade preview ────────────────────────────────────────────────

    @Test
    @DisplayName("happy downgrade preview: returns exactly the vaults/susu groups that would be frozen, commits nothing")
    void happyDowngradePreview() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(premiumSubscription()));
        when(vaultFreezingService.previewExcessVaults(USER_ID, 2, 1))
                .thenReturn(List.of(new FrozenVaultPreview("v-3", "Old Vault", "STANDARD")));
        when(susuFreezingService.previewExcessGroups(USER_ID, 1))
                .thenReturn(List.of(new FrozenSusuGroupPreview("g-2", "Old Group")));

        var preview = service.previewDowngrade(USER_ID);

        assertThat(preview.committed()).isFalse();
        assertThat(preview.vaultsToBeFrozen()).hasSize(1);
        assertThat(preview.susuGroupsToBeFrozen()).hasSize(1);
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("downgrade preview does not call freezeExcessVaults/freezeExcessGroups (read-only)")
    void previewDoesNotFreeze() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(premiumSubscription()));
        when(vaultFreezingService.previewExcessVaults(eq(USER_ID), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(susuFreezingService.previewExcessGroups(eq(USER_ID), anyInt())).thenReturn(List.of());

        service.previewDowngrade(USER_ID);

        verify(vaultFreezingService, never()).freezeExcessVaults(any(), anyInt(), anyInt());
        verify(susuFreezingService, never()).freezeExcessGroups(any(), anyInt());
    }

    @Test
    @DisplayName("downgrade preview on an already-FREE user throws SUBSCRIPTION_ALREADY_FREE (409)")
    void downgradePreviewAlreadyFreeThrows() {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(freeSubscription()));

        assertThatThrownBy(() -> service.previewDowngrade(USER_ID))
                .isInstanceOf(StashApiException.class)
                .satisfies(ex -> {
                    var e = (StashApiException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_ALREADY_FREE);
                });
    }

    // ── downgrade commit ─────────────────────────────────────────────────

    @Test
    @DisplayName("happy downgrade commit: tier flips to FREE, vaults+susu frozen, sessions revoked, tier synced")
    void happyDowngradeCommit() {
        when(subscriptionRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(premiumSubscription()));
        when(vaultFreezingService.previewExcessVaults(USER_ID, 2, 1))
                .thenReturn(List.of(new FrozenVaultPreview("v-3", "Old Vault", "STANDARD")));
        when(susuFreezingService.previewExcessGroups(USER_ID, 1))
                .thenReturn(List.of(new FrozenSusuGroupPreview("g-2", "Old Group")));

        var result = service.commitDowngrade(USER_ID);

        assertThat(result.committed()).isTrue();
        assertThat(result.vaultsToBeFrozen()).hasSize(1);
        assertThat(result.susuGroupsToBeFrozen()).hasSize(1);
        verify(vaultFreezingService).freezeExcessVaults(USER_ID, 2, 1);
        verify(susuFreezingService).freezeExcessGroups(USER_ID, 1);
        verify(userRepository).syncSubscriptionTier(USER_ID, SubscriptionTier.FREE);
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(USER_ID), eq("SUBSCRIPTION_TIER_CHANGED"), any());
        verify(subscriptionRepository).save(argThat(s -> "FREE".equals(s.getTier())));
    }

    @Test
    @DisplayName("downgrade commit on an already-FREE user throws, without touching freezing or sessions")
    void downgradeCommitAlreadyFreeThrows() {
        when(subscriptionRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(freeSubscription()));

        assertThatThrownBy(() -> service.commitDowngrade(USER_ID)).isInstanceOf(StashApiException.class);

        verifyNoInteractions(vaultFreezingService, susuFreezingService, refreshTokenRepository);
    }
}
