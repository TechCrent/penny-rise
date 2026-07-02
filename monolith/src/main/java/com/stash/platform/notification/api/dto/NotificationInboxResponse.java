package com.stash.platform.notification.api.dto;

import java.util.List;

public record NotificationInboxResponse(
        List<NotificationListItem> notifications,
        String nextCursor,
        boolean hasMore,
        long unreadCount
) {}
