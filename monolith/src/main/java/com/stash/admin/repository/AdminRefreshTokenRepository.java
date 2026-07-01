package com.stash.admin.repository;

import com.stash.admin.domain.AdminRefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminRefreshTokenRepository extends JpaRepository<AdminRefreshTokenEntity, UUID> {

    Optional<AdminRefreshTokenEntity> findByTokenHash(String tokenHash);

    @Query("""
            SELECT t FROM AdminRefreshTokenEntity t
            WHERE t.adminAccountId = :adminId
              AND t.revokedAt IS NULL
            """)
    List<AdminRefreshTokenEntity> findActiveByAdmin(@Param("adminId") UUID adminId);
}
