package com.stash.platform.user.repository;

import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link User} entities.
 *
 * <p>Soft-delete contract: every method on this interface applies
 * {@code AND u.deletedAt IS NULL} by default. Deleted users are
 * invisible to all normal queries. Callers must never add this
 * filter manually — the repository owns it.
 *
 * <p>The one exception is {@link #findByIdIncludingDeleted}, which
 * is intentionally unrestricted for admin/audit use cases.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // ── Soft-delete-aware finders ─────────────────────────────────────────

    /**
     * Finds an active user by email address.
     * Returns empty if the user does not exist or has been soft-deleted.
     *
     * <p>Used by the login and signup flows.
     *
     * @param email lowercased email address
     */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmail(@Param("email") String email);

    /**
     * Finds an active user by phone number (E.164 format).
     * Returns empty if not found or soft-deleted.
     *
     * @param phone E.164-formatted phone number
     */
    @Query("SELECT u FROM User u WHERE u.phone = :phone AND u.deletedAt IS NULL")
    Optional<User> findByPhone(@Param("phone") String phone);

    /**
     * Finds an active user by their approved Ghana Card number.
     * Returns empty if not found or soft-deleted.
     *
     * <p>This is only populated after KYC approval. Do not use it as
     * a primary lookup key before KYC is complete.
     *
     * @param ghanaCardNumber as stored after KYC approval
     */
    @Query("SELECT u FROM User u WHERE u.ghanaCardNumber = :ghanaCardNumber AND u.deletedAt IS NULL")
    Optional<User> findByGhanaCardNumber(@Param("ghanaCardNumber") String ghanaCardNumber);

    /**
     * Checks whether a non-deleted user with this email already exists.
     * Used by the signup flow to return a conflict error before attempting insert.
     *
     * @param email lowercased email address
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    boolean existsByEmail(@Param("email") String email);

    /**
     * Checks whether a non-deleted user with this phone already exists.
     *
     * @param phone E.164-formatted phone number
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.phone = :phone AND u.deletedAt IS NULL")
    boolean existsByPhone(@Param("phone") String phone);

    // ── Admin / audit queries ─────────────────────────────────────────────

    /**
     * Finds a user by ID regardless of soft-delete status.
     * Use ONLY in admin, audit, or deletion-flow contexts.
     *
     * @param id user UUID
     */
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdIncludingDeleted(@Param("id") UUID id);

    /**
     * Returns all active users with the given KYC status.
     * Used by the admin dashboard to list users pending KYC review.
     *
     * @param kycStatus the KYC status to filter by
     */
    @Query("SELECT u FROM User u WHERE u.kycStatus = :kycStatus AND u.deletedAt IS NULL")
    List<User> findByKycStatus(@Param("kycStatus") KycStatus kycStatus);

    /**
     * Returns all active users with the given account status.
     * Used by admin operational queries (e.g. list all SUSPENDED accounts).
     *
     * @param accountStatus the account status to filter by
     */
    @Query("SELECT u FROM User u WHERE u.accountStatus = :accountStatus AND u.deletedAt IS NULL")
    List<User> findByAccountStatus(@Param("accountStatus") AccountStatus accountStatus);

    // ── Vault creation support (v0.3-027) ─────────────────────────────────

    /**
     * Locks the user row with SELECT FOR UPDATE for the duration of the
     * calling transaction. Used by vault creation to serialise concurrent
     * vault-count checks from the same user.
     */
    @Query(value = "SELECT id FROM user_module.users WHERE id = :userId FOR UPDATE",
           nativeQuery = true)
    UUID lockUserRow(@Param("userId") UUID userId);

    /**
     * Finds the user and loads kyc_status and subscription_tier in one query.
     */
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForVaultCreation(@Param("userId") UUID userId);

    /**
     * Admin search: full-text filter on email/phone, optional status filters.
     * Soft-deleted users are excluded. All parameters are nullable; a null value
     * means "no filter on that field".
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (:search IS NULL
                   OR lower(u.email) LIKE lower(concat('%', :search, '%'))
                   OR u.phone LIKE concat('%', :search, '%'))
              AND (:kycStatus IS NULL OR u.kycStatus = :kycStatus)
              AND (:accountStatus IS NULL OR u.accountStatus = :accountStatus)
            ORDER BY u.createdAt DESC
            """)
    Page<User> searchForAdmin(@Param("search") String search,
                               @Param("kycStatus") KycStatus kycStatus,
                               @Param("accountStatus") AccountStatus accountStatus,
                               Pageable pageable);

    // ── Admin status mutations ─────────────────────────────────────────────

    /**
     * Transitions the user to SUSPENDED only if they are not already suspended.
     * The conditional WHERE prevents concurrent double-suspends from both succeeding.
     *
     * @return 1 if the transition happened, 0 if the user was already SUSPENDED or not found
     */
    @Modifying
    @Query("""
            UPDATE User u SET u.accountStatus = :suspended
            WHERE u.id = :id AND u.accountStatus <> :suspended AND u.deletedAt IS NULL
            """)
    int suspendIfNotAlreadySuspended(@Param("id") UUID id,
                                      @Param("suspended") AccountStatus suspended);

    /**
     * Transitions the user back to ACTIVE only if they are currently SUSPENDED.
     *
     * @return 1 if the transition happened, 0 if the user was not SUSPENDED or not found
     */
    @Modifying
    @Query("""
            UPDATE User u SET u.accountStatus = :active
            WHERE u.id = :id AND u.accountStatus = :suspended AND u.deletedAt IS NULL
            """)
    int restoreIfSuspended(@Param("id") UUID id,
                            @Param("active") AccountStatus active,
                            @Param("suspended") AccountStatus suspended);

    // ── Deletion flow (v0.5-019) ──────────────────────────────────────────

    /**
     * Soft-deletes the user by setting deletedAt, but only if not already deleted.
     * Idempotent: a second call on an already-deleted user is a no-op (returns 0).
     *
     * @return 1 if the user was deleted, 0 if already deleted or not found
     */
    @Modifying
    @Query("UPDATE User u SET u.deletedAt = :now WHERE u.id = :userId AND u.deletedAt IS NULL")
    int softDeleteIfNotAlready(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            SELECT u FROM User u
            WHERE u.kycStatus = :kycStatus
              AND u.id <> :callerId
              AND u.deletedAt IS NULL
              AND (lower(u.displayName) LIKE lower(concat(:q, '%'))
                   OR lower(u.email) LIKE lower(concat(:q, '%')))
            ORDER BY u.displayName ASC
            """)
    List<User> searchEligibleRecipients(
            @Param("q")         String    q,
            @Param("callerId")  UUID      callerId,
            @Param("kycStatus") KycStatus kycStatus,
            Pageable pageable);

    // ── Subscription sync (v0.5-029) ────────────────────────────────────────

    /**
     * Keeps the denormalised users.subscription_tier read cache in sync with
     * the authoritative user_module.subscriptions row. Called by
     * SubscriptionService after every successful upgrade/downgrade commit.
     */
    @Modifying
    @Query("UPDATE User u SET u.subscriptionTier = :tier WHERE u.id = :userId")
    int syncSubscriptionTier(@Param("userId") UUID userId, @Param("tier") SubscriptionTier tier);
}