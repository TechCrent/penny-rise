package com.stash.admin.event;

import java.time.Instant;
import java.util.UUID;

/** Published via ApplicationEventPublisher after a user is suspended by an admin. */
public record UserSuspendedEvent(UUID userId, UUID adminAccountId, String reason, Instant occurredAt) {}
