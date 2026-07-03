package com.stash.platform.subscription.domain;

import com.stash.shared.uuidv7.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for user_module.subscriptions (V41).
 *
 * <p>Exactly one row per user, enforced by subscriptions_user_uk
 * (UNIQUE(user_id)) — this is a mutable "current state" row, not an
 * append-only history table. Upgrade/downgrade/renewal update the existing
 * row in place via {@link #activatePremium} / {@link #activateFree}, they
 * never insert a second row for the same user. See V41's migration
 * comment for why: the AC's literal, twice-stated UNIQUE(user_id) design
 * doesn't ask for tier-change history.
 */
@Entity
@Table(schema = "user_module", name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    public static final String TIER_FREE = "FREE";
    public static final String TIER_PREMIUM = "PREMIUM";

    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_PAYSTACK_SUB = "PAYSTACK_SUB";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tier", nullable = false, length = 50)
    private String tier;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "external_subscription_reference", length = 255)
    private String externalSubscriptionReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Used by SignupService — every new user gets a FREE row synchronously at signup. */
    public static Subscription createInitialFree(UUID userId, Instant now) {
        Subscription s = new Subscription();
        s.id = UuidV7Generator.generate();
        s.userId = userId;
        s.tier = TIER_FREE;
        s.startedAt = now;
        s.endsAt = null;
        s.source = SOURCE_SYSTEM;
        s.externalSubscriptionReference = null;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    public boolean isPremium() {
        return TIER_PREMIUM.equals(tier);
    }

    public boolean isFree() {
        return TIER_FREE.equals(tier);
    }

    /** Updates this row in place — see class javadoc for why there's no second row. */
    public void activatePremium(String externalSubscriptionReference, Instant now) {
        this.tier = TIER_PREMIUM;
        this.startedAt = now;
        this.endsAt = null;
        this.source = SOURCE_PAYSTACK_SUB;
        this.externalSubscriptionReference = externalSubscriptionReference;
        this.updatedAt = now;
    }

    /** Updates this row in place — see class javadoc for why there's no second row. */
    public void activateFree(Instant now) {
        this.tier = TIER_FREE;
        this.startedAt = now;
        this.endsAt = null;
        this.source = SOURCE_SYSTEM;
        this.externalSubscriptionReference = null;
        this.updatedAt = now;
    }
}
