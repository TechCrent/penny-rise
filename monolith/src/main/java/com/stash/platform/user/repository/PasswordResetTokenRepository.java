package com.stash.platform.user.repository;

import com.stash.platform.user.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** All unconsumed tokens for a user, regardless of expiry. Used to invalidate on new request. */
    @Query("SELECT t FROM PasswordResetToken t WHERE t.userId = :userId AND t.consumedAt IS NULL")
    List<PasswordResetToken> findActiveByUserId(@Param("userId") UUID userId);

    /** Counts tokens issued to a user since a given instant — rate-limit check. */
    @Query("SELECT COUNT(t) FROM PasswordResetToken t WHERE t.userId = :userId AND t.expiresAt > :since")
    long countByUserIdSince(@Param("userId") UUID userId, @Param("since") Instant since);
}