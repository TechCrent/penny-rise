package com.stash.platform.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.platform.notification.api.dto.NotificationInboxResponse;
import com.stash.platform.notification.api.dto.NotificationListItem;
import com.stash.platform.notification.repository.NotificationCursorRow;
import com.stash.platform.notification.repository.NotificationInboxRow;
import com.stash.platform.notification.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationInboxService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT     = 100;

    private final NotificationRepository repository;
    private final ObjectMapper           objectMapper;
    private final Clock                  clock;

    public NotificationInboxService(NotificationRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository   = repository;
        this.objectMapper = objectMapper;
        this.clock        = clock;
    }

    public NotificationInboxResponse listInbox(UUID userId, boolean unreadOnly, String cursorId, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);

        Instant cursorCreatedAt = null;
        UUID    cursorUuid      = null;
        if (cursorId != null && !cursorId.isBlank()) {
            UUID parsedCursorId = parseCursorId(cursorId);
            NotificationCursorRow position = repository.findCursorPosition(parsedCursorId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "INVALID_CURSOR: cursor notification id " + cursorId + " was not found for this user."));
            cursorUuid      = position.getId();
            cursorCreatedAt = position.getCreatedAt();
        }

        List<NotificationInboxRow> rows = repository.findInboxPage(userId, unreadOnly, cursorCreatedAt, cursorUuid, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<NotificationInboxRow> page = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor  = hasMore ? page.get(page.size() - 1).getId().toString() : null;
        long   unreadCount = repository.countUnread(userId);

        return new NotificationInboxResponse(page.stream().map(this::toListItem).toList(), nextCursor, hasMore, unreadCount);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        assertOwnedByUser(notificationId, userId);
        // 0 rows updated → already read → idempotent, not an error
        repository.markReadIfUnread(notificationId, userId, Instant.now(clock));
    }

    @Transactional
    public void markAllRead(UUID userId) {
        // WHERE read_at IS NULL self-limits: naturally idempotent, no error when 0 rows
        repository.markAllReadForUser(userId, Instant.now(clock));
    }

    private void assertOwnedByUser(UUID notificationId, UUID userId) {
        // Deliberately 404 for both "doesn't exist" and "belongs to someone else"
        // — matches the customer-endpoint ownership convention (System Design §7.3)
        // and the AC's explicit DoD: "cross-user access (404)".
        if (!repository.existsByIdAndUserId(notificationId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "NOTIFICATION_NOT_FOUND: No notification with id " + notificationId + " for this user.");
        }
    }

    private int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        return Math.min(Math.max(requested, 1), MAX_LIMIT);
    }

    private UUID parseCursorId(String cursorId) {
        try {
            return UUID.fromString(cursorId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR: not a valid UUID.");
        }
    }

    private NotificationListItem toListItem(NotificationInboxRow row) {
        JsonNode data;
        try {
            data = objectMapper.readTree(row.getPayload());
        } catch (Exception e) {
            data = objectMapper.createObjectNode(); // degrade gracefully, same as v0.5-011
        }
        return new NotificationListItem(row.getId(), row.getNotificationType(), row.getTitle(),
                row.getBody(), data, row.getChannel(), row.getReadAt(), row.getCreatedAt());
    }
}
