package com.stash.platform.subscription.service;

import com.stash.platform.subscription.policy.SubscriptionPolicy;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * The single enforcement point for every free-tier limit and FROZEN-status
 * block on the platform, per this issue's DoD ("no scattered per-endpoint
 * limit logic"). Reads limit numbers from SubscriptionPolicy (v0.5-029) —
 * it owns no numbers itself.
 *
 * <p><strong>Concurrency, by design, NOT by locking here:</strong> this
 * class does no locking of its own. VaultCreationService already acquires
 * {@code userRepo.lockUserRow(userId)} (SELECT FOR UPDATE) before its
 * count-then-check-then-insert sequence — this class's job is only to
 * compare an already-fresh count against the right limit for the given
 * tier, inside that same lock. SusuGroupCreationService is updated in this
 * same change to acquire the identical lock (it previously had none — a
 * real, pre-existing concurrency gap, not something this issue introduced).
 * Callers MUST hold that lock (or an equivalent one) before calling
 * assertWithinLimit; this class cannot enforce that from the outside.
 */
@Service
public class SubscriptionLimitChecker {

    private static final String UPGRADE_URL = "stash://subscription/upgrade";

    private final SubscriptionPolicy policy;

    public SubscriptionLimitChecker(SubscriptionPolicy policy) {
        this.policy = policy;
    }

    public void assertStandardVaultWithinLimit(SubscriptionTier tier, long currentCount) {
        int limit = policy.standardVaultLimit(tier);
        if (currentCount >= limit) {
            throw limitExceeded(ErrorCode.VAULT_TIER_LIMIT_EXCEEDED,
                    "You've reached your plan's limit of " + limit + " STANDARD vault(s). " +
                    "Upgrade to Premium for unlimited vaults.");
        }
    }

    public void assertLockedVaultWithinLimit(SubscriptionTier tier, long currentCount) {
        int limit = policy.lockedVaultLimit(tier);
        if (currentCount >= limit) {
            throw limitExceeded(ErrorCode.VAULT_TIER_LIMIT_EXCEEDED,
                    "You've reached your plan's limit of " + limit + " LOCKED vault(s). " +
                    "Upgrade to Premium for unlimited vaults.");
        }
    }

    public void assertSusuOrganiserWithinLimit(SubscriptionTier tier, long currentCount) {
        int limit = policy.susuOrganiserLimit(tier);
        if (currentCount >= limit) {
            throw limitExceeded(ErrorCode.SUSU_TIER_LIMIT_EXCEEDED,
                    "You've reached your plan's limit of " + limit + " active susu group(s) as organiser. " +
                    "Complete or cancel an existing group, or upgrade to Premium, to create a new one.");
        }
    }

    /** Called from deposit, withdrawal, and early-exit-request endpoints. */
    public void assertVaultNotFrozen(String vaultStatus) {
        if ("FROZEN".equals(vaultStatus)) {
            throw new StashApiException(ErrorCode.VAULT_FROZEN,
                    "This vault is frozen because it exceeds your plan's limit. " +
                    "Upgrade to Premium to unfreeze it.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("upgrade_url", UPGRADE_URL));
        }
    }

    /** Called from the contribution-payment endpoint. */
    public void assertSusuGroupNotFrozen(String susuGroupStatus) {
        if ("FROZEN".equals(susuGroupStatus)) {
            throw new StashApiException(ErrorCode.SUSU_FROZEN,
                    "This susu group is frozen because its organiser exceeds their plan's limit. " +
                    "The organiser can upgrade to Premium to unfreeze it.",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    Map.of("upgrade_url", UPGRADE_URL));
        }
    }

    private StashApiException limitExceeded(ErrorCode code, String message) {
        return new StashApiException(code, message, HttpStatus.UNPROCESSABLE_ENTITY,
                Map.of("upgrade_url", UPGRADE_URL));
    }
}
