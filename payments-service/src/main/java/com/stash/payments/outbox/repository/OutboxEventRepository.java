package com.stash.payments.outbox.repository;

import com.stash.payments.outbox.domain.OutboxEventEntity;
import com.stash.payments.outbox.domain.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Relay worker poll query — used by {@code OutboxRelay} (v0.3-011).
     *
     * <p>SELECT FOR UPDATE SKIP LOCKED is intentional: multiple relay
     * instances can run safely in parallel; each picks a distinct batch.
     * The partial index {@code outbox_events_relay_polling_idx}
     * (created in v0.3-006) covers this exact query shape.
     */
    @Query(value = """
            SELECT * FROM outbox.outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> findPendingBatch(@Param("batchSize") int batchSize);
}
