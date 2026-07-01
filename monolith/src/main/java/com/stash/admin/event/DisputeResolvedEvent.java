package com.stash.admin.event;

import java.time.Instant;
import java.util.UUID;

public record DisputeResolvedEvent(UUID disputeId, UUID raisedByUserId, UUID resolvedByAdminId,
                                    String resolutionJson, Instant occurredAt) {}
