package com.stash.platform.vault.repository;

import com.stash.platform.vault.domain.VaultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaultRepository extends JpaRepository<VaultEntity, UUID> {

    /**
     * Counts vaults of a given type for a user, excluding soft-deleted ones.
     * Used for free-tier limit enforcement.
     */
    @Query("SELECT COUNT(v) FROM VaultEntity v " +
           "WHERE v.ownerUserId = :userId " +
           "AND v.vaultType = :vaultType " +
           "AND v.deletedAt IS NULL")
    long countByOwnerUserIdAndVaultType(@Param("userId")    UUID userId,
                                         @Param("vaultType") String vaultType);

    /**
     * Finds vaults for the list screen, excluding soft-deleted.
     */
    List<VaultEntity> findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerUserId);

    Optional<VaultEntity> findByIdAndOwnerUserIdAndDeletedAtIsNull(UUID id, UUID ownerUserId);
}
