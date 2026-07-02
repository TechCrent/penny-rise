package com.stash.platform.notification.repository;

import java.time.Instant;
import java.util.UUID;

/** Projection interface for the native keyset inbox query. */
public interface NotificationInboxRow {
    UUID   getId();
    String getNotificationType();
    String getChannel();
    String getTitle();
    String getBody();
    String getPayload(); // raw JSONB text — parsed to JsonNode in the service layer
    Instant getReadAt();
    Instant getCreatedAt();
}
