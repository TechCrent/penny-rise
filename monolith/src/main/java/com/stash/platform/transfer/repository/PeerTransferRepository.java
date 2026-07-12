package com.stash.platform.transfer.repository;

import com.stash.platform.transfer.domain.PeerTransferEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeerTransferRepository extends JpaRepository<PeerTransferEntity, UUID> {

    Optional<PeerTransferEntity> findByIdempotencyKey(String idempotencyKey);

    /** Sent transfers for a user, cursor-paginated newest first. */
    @Query("""
            SELECT t FROM PeerTransferEntity t
            WHERE t.senderUserId = :userId
              AND (:fromDate IS NULL OR t.createdAt >= :fromDate)
              AND (:toDate   IS NULL OR t.createdAt <= :toDate)
              AND (:cursorTime IS NULL
                   OR t.createdAt < :cursorTime
                   OR (t.createdAt = :cursorTime AND t.id < :cursorId))
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<PeerTransferEntity> findSent(
            @Param("userId")     UUID userId,
            @Param("fromDate")   Instant fromDate,
            @Param("toDate")     Instant toDate,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId")   UUID    cursorId,
            Pageable pageable);

    /** Received transfers for a user, cursor-paginated newest first. */
    @Query("""
            SELECT t FROM PeerTransferEntity t
            WHERE t.recipientUserId = :userId
              AND (:fromDate IS NULL OR t.createdAt >= :fromDate)
              AND (:toDate   IS NULL OR t.createdAt <= :toDate)
              AND (:cursorTime IS NULL
                   OR t.createdAt < :cursorTime
                   OR (t.createdAt = :cursorTime AND t.id < :cursorId))
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<PeerTransferEntity> findReceived(
            @Param("userId")     UUID userId,
            @Param("fromDate")   Instant fromDate,
            @Param("toDate")     Instant toDate,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId")   UUID    cursorId,
            Pageable pageable);

    /**
     * All transfers for a user (sent + received), cursor-paginated via UNION ALL.
     *
     * <p>Every named parameter used more than once in a native query expands
     * to its own independent JDBC positional placeholder per occurrence —
     * Postgres doesn't know two {@code ?}s came from the same {@code :name}.
     * The comparison-side occurrences here were already cast, but the bare
     * {@code :fromDate IS NULL} / {@code :toDate IS NULL} / {@code :cursorTime
     * IS NULL} occurrences were not, so each of those still needed its own
     * cast — Postgres fails to infer a type for a parameter whose only
     * context is a bare {@code IS NULL} check.
     */
    @Query(value = """
            SELECT * FROM (
                SELECT * FROM transfer.peer_transfers
                WHERE sender_user_id = :userId
                  AND (CAST(:fromDate AS TIMESTAMPTZ) IS NULL OR created_at >= CAST(:fromDate AS TIMESTAMPTZ))
                  AND (CAST(:toDate   AS TIMESTAMPTZ) IS NULL OR created_at <= CAST(:toDate   AS TIMESTAMPTZ))
                UNION ALL
                SELECT * FROM transfer.peer_transfers
                WHERE recipient_user_id = :userId
                  AND (CAST(:fromDate AS TIMESTAMPTZ) IS NULL OR created_at >= CAST(:fromDate AS TIMESTAMPTZ))
                  AND (CAST(:toDate   AS TIMESTAMPTZ) IS NULL OR created_at <= CAST(:toDate   AS TIMESTAMPTZ))
            ) combined
            WHERE (CAST(:cursorTime AS TIMESTAMPTZ) IS NULL
                   OR combined.created_at < CAST(:cursorTime AS TIMESTAMPTZ)
                   OR (combined.created_at = CAST(:cursorTime AS TIMESTAMPTZ)
                       AND combined.id < CAST(:cursorId AS UUID)))
            ORDER BY combined.created_at DESC, combined.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<PeerTransferEntity> findAllByUser(
            @Param("userId")     UUID    userId,
            @Param("fromDate")   Instant fromDate,
            @Param("toDate")     Instant toDate,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId")   UUID    cursorId,
            @Param("limit")      int     limit);
}
