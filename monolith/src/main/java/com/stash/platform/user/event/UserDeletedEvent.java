package com.stash.platform.user.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted after all deletion cleanup steps succeed. Downstream consumers
 * (challenge module, notification module) can react to this event via
 * @TransactionalEventListener or a RabbitMQ binding on challenge.completed-style routing.
 */
public record UserDeletedEvent(UUID userId, Instant occurredAt) {}
