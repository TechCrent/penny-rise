package com.stash.platform.user.repository;

import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}