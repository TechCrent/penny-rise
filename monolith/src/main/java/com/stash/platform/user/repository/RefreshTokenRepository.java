package com.stash.platform.user.repository;

import com.stash.platform.user.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}