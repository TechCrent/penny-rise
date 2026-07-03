package com.stash.platform.vault.repository;

import com.stash.platform.vault.domain.VaultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
     * <p>When {@code includeClosed = false}: returns ACTIVE, EARLY_EXIT_PENDING,
     * and FROZEN vaults that have not been soft-deleted (deleted_at IS NULL).
     * When {@code includeClosed = true}: also returns CLOSED vaults, including
     * soft-deleted ones (for history display).
     *
     * <p><strong>FROZEN added in v0.5-032:</strong> when FROZEN shipped in
     * v0.5-029, this query wasn't updated for it — a FROZEN vault (over the
     * free-tier limit after downgrade) fell through to neither branch and
     * was invisible in the default list, contradicting the downgrade flow's
     * own requirement that frozen resources stay visible on the vault list,
     * just marked as frozen. Unlike CLOSED (intentionally archived, hidden
     * unless includeClosed=true), FROZEN is an active-but-blocked state and
     * belongs in the default view.
     */
    @Query("""
            SELECT v FROM VaultEntity v
            WHERE v.ownerUserId = :userId
            AND (
                :includeClosed = true
                OR (v.status IN ('ACTIVE', 'EARLY_EXIT_PENDING', 'FROZEN') AND v.deletedAt IS NULL)
            )
            ORDER BY v.createdAt DESC
            """)
    List<VaultEntity> findVaultsForUser(@Param("userId")        UUID    userId,
                                         @Param("includeClosed") boolean includeClosed);

    /**
     * Selects ACTIVE LOCKED vaults that have at least one unlock condition set.
     * Used by the auto-unlock worker as the candidate set — balance conditions
     * are then checked per-vault against the Payments Service.
     *
     * <p>SELECT FOR UPDATE SKIP LOCKED — safe for concurrent worker instances.
     * Excludes vaults in EARLY_EXIT_PENDING (those go through the early-exit flow).
     * Excludes vaults already unlocked (unlocked_at IS NOT NULL).
     *
     * <p>For date-based conditions, we can pre-filter here (unlock_by_date <= now).
     * For amount-based conditions, we must fetch the balance from Payments, so
     * we include all amount-based vaults as candidates and check balance in the worker.
     */
    @Query(value = """
            SELECT * FROM vault.vaults
            WHERE vault_type = 'LOCKED'
              AND status     = 'ACTIVE'
              AND deleted_at IS NULL
              AND unlocked_at IS NULL
              AND (
                  -- Date condition candidate: date is set and may have passed
                  (unlock_by_date IS NOT NULL AND unlock_by_date <= :now)
                  OR
                  -- Amount condition candidate: amount is set (balance check happens in worker)
                  unlock_target_amount IS NOT NULL
              )
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<VaultEntity> findUnlockCandidates(@Param("now") Instant now,
                                            @Param("batchSize") int batchSize);

    /**
     * ACTIVE vaults of a given type, oldest first — used by
     * VaultFreezingService (v0.5-029) to determine which vaults beyond the
     * limit get frozen. Oldest-first means the user's newest vaults stay
     * active; only the longest-running excess ones freeze.
     */
    @Query("SELECT v FROM VaultEntity v " +
           "WHERE v.ownerUserId = :userId " +
           "AND v.vaultType = :vaultType " +
           "AND v.status = 'ACTIVE' " +
           "AND v.deletedAt IS NULL " +
           "ORDER BY v.createdAt ASC")
    List<VaultEntity> findActiveByOwnerUserIdAndVaultTypeOrderByCreatedAtAsc(
            @Param("userId") UUID userId, @Param("vaultType") String vaultType);

    /**
     * Freezes a single ACTIVE vault — a no-op (returns 0) if the vault is
     * already in some other status, so this is safe to call without a
     * separate existence/status check first.
     */
    @Modifying
    @Query("UPDATE VaultEntity v SET v.status = 'FROZEN' WHERE v.id = :vaultId AND v.status = 'ACTIVE'")
    int freezeIfActive(@Param("vaultId") UUID vaultId);
}
