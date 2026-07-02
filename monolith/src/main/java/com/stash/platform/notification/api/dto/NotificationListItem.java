package com.stash.platform.notification.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Field names match the AC's wire contract (`type`/`data`), not the DB
 * column names (`notification_type`/`payload`) — same resolution as v0.5-013:
 * DB stays per Schema doc, API uses friendlier names, mapped once in service.
 */
public record NotificationListItem(
        UUID id,
        String type,
        String title,
        String body,
        JsonNode data,
        String channel,
        Instant readAt,
        Instant createdAt
) {}
