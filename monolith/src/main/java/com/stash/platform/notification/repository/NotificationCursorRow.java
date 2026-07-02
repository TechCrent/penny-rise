package com.stash.platform.notification.repository;

import java.time.Instant;
import java.util.UUID;

/** Minimal JPQL projection for cursor resolution — avoids Object[] casting. */
public interface NotificationCursorRow {
    UUID   getId();
    Instant getCreatedAt();
}
