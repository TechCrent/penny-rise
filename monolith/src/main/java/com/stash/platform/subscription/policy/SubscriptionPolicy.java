package com.stash.platform.subscription.policy;

import com.stash.platform.user.domain.SubscriptionTier;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for tier-based limits. v0.5-030's
 * SubscriptionLimitChecker is expected to read from this class rather than
 * redefine its own copies of these numbers — matching (and, for vaults,
 * formalising) the inline placeholder constants that already existed in
 * VaultCreationService and SusuGroupCreationService before this class:
 *
 * <ul>
 *   <li>Vault limits: verified against VaultCreationService's existing
 *       enforceFreeTierLimits — FREE only, PREMIUM skips the check entirely
 *       (genuinely unlimited).</li>
 *   <li>Susu organiser limit: verified against SusuGroupCreationService's
 *       existing FREE_TIER_ORGANISER_LIMIT=1 / PREMIUM_TIER_ORGANISER_LIMIT=3
 *       constants — PREMIUM is capped at 3, NOT unlimited, despite this
 *       area's Description text summarising it as "no vault or susu
 *       limits". Two independent, concrete sources (the AC's own "PREMIUM
 *       → max 3" bullet, and this already-shipped code) agree on 3; the
 *       Description's blanket phrase is the outlier and is treated as an
 *       imprecise summary, not the literal spec.</li>
 *   <li>Transfer quota: FREE=5/month is already shipped
 *       (PeerTransferService.FREE_QUOTA_LIMIT). PREMIUM=20/month is new —
 *       no PREMIUM-tier transfer behaviour existed before this, confirmed
 *       by a repo-wide search finding no PREMIUM-conditional logic and no
 *       existing test asserting unlimited/fee-exempt PREMIUM transfers.
 *       The real, current issue text for both v0.5-029 and v0.5-030
 *       explicitly and consistently states 20/month for PREMIUM — built
 *       literally as specified.</li>
 * </ul>
 */
@Component
public class SubscriptionPolicy {

    public static final int FREE_STANDARD_VAULT_LIMIT = 2;
    public static final int FREE_LOCKED_VAULT_LIMIT = 1;
    public static final int FREE_SUSU_ORGANISER_LIMIT = 1;
    public static final int PREMIUM_SUSU_ORGANISER_LIMIT = 3;
    public static final int FREE_TRANSFERS_PER_MONTH = 5;
    public static final int PREMIUM_TRANSFERS_PER_MONTH = 20;

    public int standardVaultLimit(SubscriptionTier tier) {
        return tier == SubscriptionTier.PREMIUM ? Integer.MAX_VALUE : FREE_STANDARD_VAULT_LIMIT;
    }

    public int lockedVaultLimit(SubscriptionTier tier) {
        return tier == SubscriptionTier.PREMIUM ? Integer.MAX_VALUE : FREE_LOCKED_VAULT_LIMIT;
    }

    public int susuOrganiserLimit(SubscriptionTier tier) {
        return tier == SubscriptionTier.PREMIUM ? PREMIUM_SUSU_ORGANISER_LIMIT : FREE_SUSU_ORGANISER_LIMIT;
    }

    public int transfersPerMonth(SubscriptionTier tier) {
        return tier == SubscriptionTier.PREMIUM ? PREMIUM_TRANSFERS_PER_MONTH : FREE_TRANSFERS_PER_MONTH;
    }
}
