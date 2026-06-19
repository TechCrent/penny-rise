package com.stash.platform.user.service;

import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.SubscriptionTier;

import java.util.UUID;

/**
 * Parsed claims from a verified JWT access token.
 *
 * <p>This record is what the rest of the application sees after the
 * JWT has been validated. It is the authorisation context for a request.
 *
 * <p>Per System Design §7.1:
 * <ul>
 *   <li>{@code sub} — user UUID</li>
 *   <li>{@code iat} — issued-at (not exposed here; implicit in the token)</li>
 *   <li>{@code exp} — expiry (verified on parse; not exposed here)</li>
 *   <li>{@code kyc_status} — denormalised for no-DB authorisation checks</li>
 *   <li>{@code subscription_tier} — denormalised</li>
 *   <li>{@code account_status} — denormalised</li>
 * </ul>
 */
public record AccessTokenClaims(
        UUID userId,
        KycStatus kycStatus,
        SubscriptionTier subscriptionTier,
        AccountStatus accountStatus
) {}