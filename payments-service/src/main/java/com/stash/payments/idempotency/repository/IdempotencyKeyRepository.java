package com.stash.payments.idempotency.repository;

import com.stash.payments.idempotency.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByKeyValue(String keyValue);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.keyValue = :keyValue")
    void deleteByKeyValue(@Param("keyValue") String keyValue);

    /**
     * Cleanup job query — batch delete expired rows.
     * Runs in a loop from IdempotencyCleanupJob in batches of 500
     * to avoid long-held table locks.
     */
    @Modifying
    @Query(value = """
            DELETE FROM idempotency.idempotency_keys
            WHERE id IN (
                SELECT id FROM idempotency.idempotency_keys
                WHERE expires_at < :now
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now,
                           @Param("batchSize") int batchSize);
}
