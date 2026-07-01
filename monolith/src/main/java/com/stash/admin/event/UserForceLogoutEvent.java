package com.stash.admin.event;

import java.time.Instant;
import java.util.UUID;

/** Published via ApplicationEventPublisher after an admin force-logs-out a user's sessions. */
public record UserForceLogoutEvent(UUID userId, UUID adminAccountId, int sessionsRevoked, Instant occurredAt) {}
