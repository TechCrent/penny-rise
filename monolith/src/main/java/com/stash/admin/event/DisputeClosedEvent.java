package com.stash.admin.event;

import java.time.Instant;
import java.util.UUID;

/** Status value is CLOSED_NO_ACTION; this event name follows the AC literal. */
public record DisputeClosedEvent(UUID disputeId, UUID raisedByUserId, UUID closedByAdminId,
                                  String reason, Instant occurredAt) {}
