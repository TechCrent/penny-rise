package com.stash.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminDisputeListItem(
        UUID id, UUID raisedByUserId, String disputeType, String relatedEntityType,
        UUID relatedEntityId, String subject, String status, String priority,
        UUID assignedToAdminId, Instant createdAt
) {}
