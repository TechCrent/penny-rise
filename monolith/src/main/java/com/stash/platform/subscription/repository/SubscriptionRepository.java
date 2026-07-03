package com.stash.platform.subscription.repository;

import com.stash.platform.subscription.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Exactly one row per user (subscriptions_user_uk) — this is the
     * authoritative lookup for "what tier is this user on right now".
     */
    Optional<Subscription> findByUserId(UUID userId);

    /**
     * Locks the row with SELECT FOR UPDATE for the duration of the calling
     * transaction — used by upgrade/downgrade to serialise concurrent
     * tier-change attempts from the same user (mirrors
     * UserRepository.lockUserRow's pattern for vault creation).
     */
    @Query(value = "SELECT * FROM user_module.subscriptions WHERE user_id = :userId FOR UPDATE",
           nativeQuery = true)
    Optional<Subscription> findByUserIdForUpdate(@Param("userId") UUID userId);
}
