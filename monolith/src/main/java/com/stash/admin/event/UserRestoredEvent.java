package com.stash.admin.event;

import java.time.Instant;
import java.util.UUID;

/** Published via ApplicationEventPublisher after a suspended user is restored by an admin. */
public record UserRestoredEvent(UUID userId, UUID adminAccountId, Instant occurredAt) {}
