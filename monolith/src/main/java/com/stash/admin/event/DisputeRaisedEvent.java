package com.stash.admin.event;

import java.time.Instant;
import java.util.UUID;

public record DisputeRaisedEvent(UUID disputeId, UUID raisedByUserId, String disputeType,
                                  String relatedEntityType, UUID relatedEntityId, Instant occurredAt) {}
