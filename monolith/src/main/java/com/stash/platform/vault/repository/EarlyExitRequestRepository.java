package com.stash.platform.vault.repository;

import com.stash.platform.vault.domain.EarlyExitRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarlyExitRequestRepository extends JpaRepository<EarlyExitRequestEntity, UUID> {

    /**
     * Finds the active (PENDING) early-exit request for a vault.
     * Used for duplicate check and by the release worker.
     */
    @Query("SELECT r FROM EarlyExitRequestEntity r " +
           "WHERE r.vaultId = :vaultId AND r.status = 'PENDING'")
    Optional<EarlyExitRequestEntity> findPendingByVaultId(@Param("vaultId") UUID vaultId);

    /**
     * Finds all PENDING requests whose scheduled_release_at has passed.
     * Used by the auto-release worker (v0.3-032).
     */
    @Query(value = """
            SELECT * FROM vault.locked_vault_early_exit_requests
            WHERE status = 'PENDING'
              AND scheduled_release_at <= :now
            ORDER BY scheduled_release_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EarlyExitRequestEntity> findDueRequests(@Param("now") Instant now,
                                                   @Param("batchSize") int batchSize);
}
