package com.stash.platform.subscription.service;

import com.stash.platform.subscription.api.dto.*;
import com.stash.platform.subscription.domain.Subscription;
import com.stash.platform.subscription.policy.SubscriptionPolicy;
import com.stash.platform.subscription.repository.SubscriptionRepository;
import com.stash.platform.transfer.repository.MonthlyTransferQuotaRepository;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.repository.RefreshTokenRepository;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Subscription upgrade/downgrade/status. See Subscription's javadoc for why
 * this is a single mutable row per user, not append-only history.
 *
 * <p>Every user has exactly one subscriptions row by construction: existing
 * users were backfilled by V41, new users get one synchronously at signup
 * (SignupService) — a missing row here is an invariant violation, not a
 * normal "not found" case.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MonthlyTransferQuotaRepository quotaRepository;
    private final VaultFreezingService vaultFreezingService;
    private final SusuFreezingService susuFreezingService;
    private final SubscriptionPolicy policy;
    private final SubscriptionPaystackClient paystackClient;
    private final Clock clock;

    private static final String REVOKE_REASON = "SUBSCRIPTION_TIER_CHANGED";

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                                UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                MonthlyTransferQuotaRepository quotaRepository,
                                VaultFreezingService vaultFreezingService,
                                SusuFreezingService susuFreezingService,
                                SubscriptionPolicy policy,
                                SubscriptionPaystackClient paystackClient,
                                Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.quotaRepository = quotaRepository;
        this.vaultFreezingService = vaultFreezingService;
        this.susuFreezingService = susuFreezingService;
        this.policy = policy;
        this.paystackClient = paystackClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getStatus(UUID userId) {
        Subscription current = mustFindActive(userId);
        SubscriptionTier tier = SubscriptionTier.valueOf(current.getTier());

        int monthlyLimit = policy.transfersPerMonth(tier);
        int freeRemaining = tier == SubscriptionTier.PREMIUM
                ? monthlyLimit
                : transferQuotaRemaining(userId, monthlyLimit);

        return new SubscriptionStatusResponse(
                current.getTier(), current.getStartedAt(), current.getEndsAt(),
                current.getSource(), freeRemaining);
    }

    @Transactional
    public UpgradeResponse upgrade(UUID userId, String paystackSubscriptionToken) {
        Subscription current = mustFindActiveForUpdate(userId);
        if (current.isPremium()) {
            throw new StashApiException(ErrorCode.SUBSCRIPTION_ALREADY_PREMIUM,
                    "This user is already on the PREMIUM tier.", HttpStatus.CONFLICT);
        }

        String externalRef = paystackClient.createTestSubscription(userId, paystackSubscriptionToken);
        Instant now = Instant.now(clock);

        current.activatePremium(externalRef, now);
        subscriptionRepository.save(current);
        userRepository.syncSubscriptionTier(userId, SubscriptionTier.PREMIUM);
        revokeActiveSessions(userId, now);

        return new UpgradeResponse(current.getTier(), current.getStartedAt());
    }

    @Transactional(readOnly = true)
    public DowngradePreviewResponse previewDowngrade(UUID userId) {
        Subscription current = mustFindActive(userId);
        assertIsPremium(current);
        return buildPreview(userId, false);
    }

    @Transactional
    public DowngradePreviewResponse commitDowngrade(UUID userId) {
        Subscription current = mustFindActiveForUpdate(userId);
        assertIsPremium(current);

        // Computed BEFORE mutation so the committed response reflects exactly
        // what gets frozen below, not a post-mutation state that's already changed.
        DowngradePreviewResponse preview = buildPreview(userId, true);

        Instant now = Instant.now(clock);
        current.activateFree(now);
        subscriptionRepository.save(current);
        userRepository.syncSubscriptionTier(userId, SubscriptionTier.FREE);

        vaultFreezingService.freezeExcessVaults(
                userId, SubscriptionPolicy.FREE_STANDARD_VAULT_LIMIT, SubscriptionPolicy.FREE_LOCKED_VAULT_LIMIT);
        susuFreezingService.freezeExcessGroups(userId, SubscriptionPolicy.FREE_SUSU_ORGANISER_LIMIT);

        revokeActiveSessions(userId, now);

        return preview;
    }

    private DowngradePreviewResponse buildPreview(UUID userId, boolean committed) {
        var vaultsToFreeze = vaultFreezingService.previewExcessVaults(
                userId, SubscriptionPolicy.FREE_STANDARD_VAULT_LIMIT, SubscriptionPolicy.FREE_LOCKED_VAULT_LIMIT);
        var susuGroupsToFreeze = susuFreezingService.previewExcessGroups(
                userId, SubscriptionPolicy.FREE_SUSU_ORGANISER_LIMIT);

        return new DowngradePreviewResponse(vaultsToFreeze, susuGroupsToFreeze, committed);
    }

    private void assertIsPremium(Subscription current) {
        if (!current.isPremium()) {
            throw new StashApiException(ErrorCode.SUBSCRIPTION_ALREADY_FREE,
                    "This user is already on the FREE tier.", HttpStatus.CONFLICT);
        }
    }

    private Subscription mustFindActive(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "User " + userId + " has no subscription row — this should be impossible " +
                        "given V41's backfill and SignupService's mandatory row-per-signup invariant"));
    }

    private Subscription mustFindActiveForUpdate(UUID userId) {
        return subscriptionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "User " + userId + " has no subscription row — this should be impossible " +
                        "given V41's backfill and SignupService's mandatory row-per-signup invariant"));
    }

    private void revokeActiveSessions(UUID userId, Instant now) {
        refreshTokenRepository.revokeAllActiveForUser(userId, REVOKE_REASON, now);
    }

    private int transferQuotaRemaining(UUID userId, int monthlyLimit) {
        LocalDate today = LocalDate.now(clock);
        return quotaRepository.findByUserAndMonth(userId, today.getYear(), today.getMonthValue())
                .map(q -> Math.max(0, monthlyLimit - q.getFreeTransfersUsed()))
                .orElse(monthlyLimit);
    }
}
