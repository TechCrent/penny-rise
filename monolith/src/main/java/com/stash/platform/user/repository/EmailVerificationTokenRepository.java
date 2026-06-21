package com.stash.platform.user.repository;

import com.stash.platform.user.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Counts tokens issued to a user since a given instant.
     * Used by the resend rate-limit check.
     */
    @Query("""
            SELECT COUNT(t) FROM EmailVerificationToken t
            WHERE t.userId = :userId
            AND t.expiresAt > :since
            """)
    long countByUserIdSince(@Param("userId") UUID userId,
                            @Param("since") Instant since);
}
