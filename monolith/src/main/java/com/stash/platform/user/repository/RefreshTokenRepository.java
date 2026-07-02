package com.stash.platform.user.repository;

import com.stash.platform.user.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Finds all tokens with replacedById = the given id.
     * Used to walk the chain forward during replay detection.
     */
    @Query("SELECT t FROM RefreshToken t WHERE t.replacedById = :parentId")
    List<RefreshToken> findByReplacedById(@Param("parentId") UUID parentId);

    /** Finds all active (non-revoked) tokens for a user. */
    @Query("SELECT t FROM RefreshToken t WHERE t.userId = :userId AND t.revokedAt IS NULL")
    List<RefreshToken> findActiveByUserId(@Param("userId") UUID userId);

    /**
     * Revokes all active tokens for the given user in a single UPDATE, setting
     * {@code revokedReason = 'ADMIN_FORCE_LOGOUT'}. Called by admin force-logout.
     *
     * @return the number of tokens revoked (0 if the user had no active sessions)
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now, t.revokedReason = 'ADMIN_FORCE_LOGOUT'
            WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Revokes all active tokens for the given user with a caller-supplied reason.
     * Used by flows that need a reason code distinct from ADMIN_FORCE_LOGOUT, such
     * as the deletion cleanup saga (ACCOUNT_DELETED).
     *
     * @return the number of tokens revoked (0 if the user had no active sessions)
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
            SET t.revokedAt = :now, t.revokedReason = :reason
            WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllActiveForUser(@Param("userId") UUID userId,
                                @Param("reason") String reason,
                                @Param("now") Instant now);
}