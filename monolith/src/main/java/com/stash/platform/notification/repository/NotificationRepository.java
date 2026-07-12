package com.stash.platform.notification.repository;

import com.stash.platform.notification.domain.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    // ── v0.5-013: delivery tracking ──────────────────────────────────────────

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEntity n SET n.deliveryStatus = 'DELIVERED' WHERE n.id = :id")
    void markDelivered(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEntity n SET n.deliveryStatus = 'FAILED', n.deliveryAttempts = :attempts WHERE n.id = :id")
    void markFailed(@Param("id") UUID id, @Param("attempts") int attempts);

    // ── v0.5-015: inbox keyset pagination ───────────────────────────────────

    /**
     * Native SQL for the tuple (created_at, id) row-value comparison — JPQL
     * doesn't support multi-column comparisons. cursorCreatedAt/cursorId are
     * both null when no cursor is provided; the guard expression
     * `:cursorCreatedAt IS NULL OR ...` prevents the row-value comparison
     * from ever being evaluated with nulls.
     */
    @Query(value = """
            SELECT id, notification_type, channel, title, body, payload, read_at, created_at
            FROM notification.notifications
            WHERE user_id = :userId
              AND (:unreadOnly = false OR read_at IS NULL)
              AND (CAST(:cursorCreatedAt AS timestamptz) IS NULL OR (created_at, id) < (:cursorCreatedAt, :cursorId))
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<NotificationInboxRow> findInboxPage(@Param("userId") UUID userId,
                                              @Param("unreadOnly") boolean unreadOnly,
                                              @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                              @Param("cursorId") UUID cursorId,
                                              @Param("limit") int limit);

    /**
     * JPQL for cursor resolution — uses NotificationCursorRow projection
     * so Instant conversion is handled by JPA (no Timestamp cast needed).
     * Also enforces userId ownership in the same query to match the 404 behavior.
     */
    @Query("SELECT n.id AS id, n.createdAt AS createdAt FROM NotificationEntity n WHERE n.id = :id AND n.userId = :userId")
    Optional<NotificationCursorRow> findCursorPosition(@Param("id") UUID id, @Param("userId") UUID userId);

    // ── v0.5-015: unread count ───────────────────────────────────────────────

    @Query("SELECT COUNT(n) FROM NotificationEntity n WHERE n.userId = :userId AND n.readAt IS NULL")
    long countUnread(@Param("userId") UUID userId);

    // ── v0.5-015: mark-read ──────────────────────────────────────────────────

    boolean existsByIdAndUserId(UUID id, UUID userId);

    /**
     * WHERE read_at IS NULL is what makes this truly idempotent — an
     * unconditional SET would silently bump the timestamp on every call,
     * violating the AC's "already-read notifications are unaffected" requirement.
     */
    @Modifying
    @Transactional
    @Query("UPDATE NotificationEntity n SET n.readAt = :now WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
    int markReadIfUnread(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEntity n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllReadForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
