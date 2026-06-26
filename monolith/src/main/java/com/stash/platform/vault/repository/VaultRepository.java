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

    /**
     * Returns vaults for the authenticated user, supporting optional inclusion
     * of CLOSED and soft-deleted vaults.
     *
     * <p>When {@code includeClosed = false}: returns ACTIVE and EARLY_EXIT_PENDING
     * vaults that have not been soft-deleted (deleted_at IS NULL).
     * When {@code includeClosed = true}: also returns CLOSED vaults, including
     * soft-deleted ones (for history display).
     */
    @Query("""
            SELECT v FROM VaultEntity v
            WHERE v.ownerUserId = :userId
            AND (
                :includeClosed = true
                OR (v.status IN ('ACTIVE', 'EARLY_EXIT_PENDING') AND v.deletedAt IS NULL)
            )
            ORDER BY v.createdAt DESC
            """)
    List<VaultEntity> findVaultsForUser(@Param("userId")        UUID    userId,
                                         @Param("includeClosed") boolean includeClosed);
}
